package reactor.aeron.demo;

import io.aeron.driver.ThreadingMode;
import java.time.Duration;
import java.util.function.Supplier;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import reactor.aeron.AeronResources;
import reactor.aeron.AeronServer;

public class ServerThroughput {

  /**
   * Main runner.
   *
   * @param args program arguments.
   */
  public static void main(String[] args) {
    Supplier<IdleStrategy> idleStrategySupplier = () -> new BackoffIdleStrategy(1, 1, 1, 100);

    AeronResources aeronResources =
        new AeronResources()
            .useTmpDir()
            .media(
                ctx ->
                    ctx.threadingMode(ThreadingMode.DEDICATED)
                        .conductorIdleStrategy(idleStrategySupplier.get())
                        .receiverIdleStrategy(idleStrategySupplier.get())
                        .senderIdleStrategy(idleStrategySupplier.get())
                        .termBufferSparseFile(false))
            .start()
            .block();

    RateReporter reporter = new RateReporter(Duration.ofSeconds(1));

    AeronServer.create(aeronResources)
        .options("localhost", 13000, 13001)
        .handle(
            connection ->
                connection
                    .inbound()
                    .receive()
                    .doOnNext(buffer -> reporter.onMessage(1, buffer.capacity()))
                    .then(connection.onDispose()))
        .bind()
        .block()
        .onDispose()
        .doFinally(
            s -> {
              reporter.dispose();
              aeronResources.dispose();
            })
        .then(aeronResources.onDispose())
        .block();
  }
}
