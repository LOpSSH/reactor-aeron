package reactor.netty.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToByteEncoder;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.HdrHistogram.Recorder;
import org.agrona.console.ContinueBarrier;
import reactor.aeron.demo.Configurations;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyPipeline.SendOptions;
import reactor.netty.channel.BootstrapHandlers;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

public class ReactorNettyClientPing {

  private static final Recorder HISTOGRAM = new Recorder(TimeUnit.SECONDS.toNanos(10), 3);

  private static final ByteBuf PAYLOAD =
      ByteBufAllocator.DEFAULT.buffer(Configurations.MESSAGE_LENGTH);

  static {
    Random random = new Random(System.nanoTime());
    byte[] bytes = new byte[Configurations.MESSAGE_LENGTH];
    random.nextBytes(bytes);
    PAYLOAD.writeBytes(bytes);
  }

  /**
   * Main runner.
   *
   * @param args program arguments.
   */
  public static void main(String[] args) {
    System.out.println(
        "address: " + Configurations.MDC_ADDRESS + ", port: " + Configurations.MDC_PORT);

    LoopResources loopResources = LoopResources.create("reactor-netty-ping");

    Connection connection =
        TcpClient.create(ConnectionProvider.newConnection())
            .addressSupplier(
                () ->
                    InetSocketAddress.createUnresolved(
                        Configurations.MDC_ADDRESS, Configurations.MDC_PORT))
            .runOn(loopResources)
            .bootstrap(
                b ->
                    BootstrapHandlers.updateConfiguration(
                        b,
                        "channel",
                        (connectionObserver, channel) -> {
                          setupChannel(channel);
                        }))
            .doOnConnected(c -> System.out.println("connected"))
            .connectNow();

    ContinueBarrier barrier = new ContinueBarrier("Execute again?");
    do {
      System.out.println("Pinging " + Configurations.NUMBER_OF_MESSAGES + " messages");
      roundTripMessages(connection, Configurations.NUMBER_OF_MESSAGES);
      System.out.println("Histogram of RTT latencies in microseconds.");
    } while (barrier.await());

    connection.dispose();

    connection.onDispose(loopResources).onDispose().block();
  }

  private static void roundTripMessages(Connection connection, long count) {
    HISTOGRAM.reset();

    Disposable reporter = startReport();

    connection
        .outbound()
        .options(SendOptions::flushOnEach)
        .sendObject(Flux.range(0, Configurations.REQUESTED))
        .then()
        .subscribe();

    connection
        .outbound()
        .options(SendOptions::flushOnEach)
        .sendObject(
            connection
                .inbound()
                .receive()
                // .retain()
                .take(count)
                .doOnNext(
                    buffer -> {
                      long start = buffer.readLong();
                      System.out.println(buffer.readBytes(Configurations.MESSAGE_LENGTH));
                      //                      if (buffer.readableBytes() !=
                      // Configurations.MESSAGE_LENGTH) {
                      //                        throw new RuntimeException("Assertrion error!");
                      //                      }
                      long diff = System.nanoTime() - start;
                      HISTOGRAM.recordValue(diff);
                      // buffer.release();
                    })
                .map(buffer -> 1))
        .then(
            Mono.defer(
                () ->
                    Mono.delay(Duration.ofMillis(100))
                        .doOnSubscribe(s -> reporter.dispose())
                        .then()))
        .then()
        .block();
  }

  private static Disposable startReport() {
    return Flux.interval(
            Duration.ofSeconds(Configurations.WARMUP_REPORT_DELAY),
            Duration.ofSeconds(Configurations.REPORT_INTERVAL))
        .doOnNext(ReactorNettyClientPing::report)
        .doFinally(ReactorNettyClientPing::report)
        .subscribe();
  }

  private static void report(Object ignored) {
    System.out.println("---- PING/PONG HISTO ----");
    HISTOGRAM.getIntervalHistogram().outputPercentileDistribution(System.out, 5, 1000.0, false);
    System.out.println("---- PING/PONG HISTO ----");
  }

  private static void setupChannel(Channel channel) {
    final int maxFrameLength = 1024 * 1024;
    final int lengthFieldLength = 2;

    ChannelPipeline pipeline = channel.pipeline();
    pipeline.addLast(new LengthFieldPrepender(lengthFieldLength));
    pipeline.addLast(
        new LengthFieldBasedFrameDecoder(
            maxFrameLength, 0, lengthFieldLength, 0, lengthFieldLength));
    pipeline.addLast(
        new MessageToByteEncoder<Integer>() {
          @Override
          protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) {
            out.writeLong(System.nanoTime());
            out.writeBytes(PAYLOAD);
          }
        });
  }
}
