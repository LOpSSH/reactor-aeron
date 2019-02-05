package reactor.aeron.demo;

import io.aeron.driver.Configuration;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.HdrHistogram.Recorder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.console.ContinueBarrier;
import reactor.aeron.AeronClient;
import reactor.aeron.AeronConnection;
import reactor.aeron.AeronResources;
import reactor.aeron.DirectBufferHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class AeronPingClient {

  private static final Recorder HISTOGRAM = new Recorder(TimeUnit.SECONDS.toNanos(10), 3);

  /**
   * Main runner.
   *
   * @param args program arguments.
   */
  public static void main(String... args) {

    AeronResources resources =
        new AeronResources()
            .useTmpDir()
            .pollFragmentLimit(Configurations.FRAGMENT_COUNT_LIMIT)
            .singleWorker()
            .workerIdleStrategySupplier(Configurations::idleStrategy)
            .start()
            .block();

    AeronConnection connection =
        AeronClient.create(resources)
            .options(
                Configurations.MDC_ADDRESS,
                Configurations.MDC_PORT,
                Configurations.MDC_CONTROL_PORT)
            .connect()
            .block();

    System.out.println(
        "address: "
            + Configurations.MDC_ADDRESS
            + ", port: "
            + Configurations.MDC_PORT
            + ", controlPort: "
            + Configurations.MDC_CONTROL_PORT);
    System.out.println("MediaDriver THREADING_MODE: " + Configuration.THREADING_MODE_DEFAULT);
    System.out.println("Message length of " + Configurations.MESSAGE_LENGTH + " bytes");
    System.out.println("pollFragmentLimit of " + Configurations.FRAGMENT_COUNT_LIMIT);
    System.out.println(
        "Using worker idle strategy "
            + Configurations.idleStrategy().getClass()
            + "("
            + Configurations.IDLE_STRATEGY
            + ")");
    System.out.println("Request " + Configurations.REQUESTED);

    System.out.println(
        "Warming up... "
            + Configurations.WARMUP_NUMBER_OF_ITERATIONS
            + " iterations of "
            + Configurations.WARMUP_NUMBER_OF_MESSAGES
            + " messages");

    for (int i = 0; i < Configurations.WARMUP_NUMBER_OF_ITERATIONS; i++) {
      roundTripMessages(connection, Configurations.WARMUP_NUMBER_OF_MESSAGES, true);
    }

    ContinueBarrier barrier = new ContinueBarrier("Execute again?");
    do {
      System.out.println("Pinging " + Configurations.NUMBER_OF_MESSAGES + " messages");
      roundTripMessages(connection, Configurations.NUMBER_OF_MESSAGES, false);
      System.out.println("Histogram of RTT latencies in microseconds.");
    } while (barrier.await());

    connection.dispose();

    connection.onDispose(resources).onDispose().block();
  }

  private static void roundTripMessages(AeronConnection connection, long count, boolean warmup) {
    HISTOGRAM.reset();

    Disposable reporter = startReport(warmup);

    NanoTimeGeneratorHandler handler = new NanoTimeGeneratorHandler();

    connection.outbound().send(Flux.range(0, Configurations.REQUESTED), handler).then().subscribe();

    connection
        .outbound()
        .send(
            connection
                .inbound()
                .receive()
                .take(count)
                .doOnNext(
                    buffer -> {
                      long start = buffer.getLong(0);
                      long diff = System.nanoTime() - start;
                      HISTOGRAM.recordValue(diff);
                    }),
            handler)
        .then(
            Mono.defer(
                () ->
                    Mono.delay(Duration.ofMillis(100))
                        .doOnSubscribe(s -> reporter.dispose())
                        .then()))
        .then()
        .block();
  }

  private static Disposable startReport(boolean warmup) {
    return warmup
        ? () -> {}
        : Flux.interval(Duration.ofSeconds(Configurations.REPORT_INTERVAL))
            .doOnNext(AeronPingClient::report)
            .doFinally(AeronPingClient::report)
            .subscribe();
  }

  private static void report(Object ignored) {
    System.out.println("---- PING/PONG HISTO ----");
    HISTOGRAM.getIntervalHistogram().outputPercentileDistribution(System.out, 5, 1000.0, false);
    System.out.println("---- PING/PONG HISTO ----");
  }

  private static class NanoTimeGeneratorHandler implements DirectBufferHandler<Object> {
    @Override
    public int estimateLength(Object ignore) {
      return Configurations.MESSAGE_LENGTH;
    }

    @Override
    public void write(MutableDirectBuffer dstBuffer, int index, Object ignore, int length) {
      dstBuffer.putLong(index, System.nanoTime());
    }

    @Override
    public DirectBuffer map(Object ignore, int length) {
      UnsafeBuffer buffer = new UnsafeBuffer(new byte[length]);
      buffer.putLong(0, System.nanoTime());
      return buffer;
    }

    @Override
    public void dispose(Object ignore) {}
  }
}
