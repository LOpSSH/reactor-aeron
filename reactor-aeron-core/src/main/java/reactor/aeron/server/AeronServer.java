package reactor.aeron.server;

import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.reactivestreams.Publisher;
import reactor.aeron.AeronOptions;
import reactor.aeron.AeronResources;
import reactor.aeron.Connection;
import reactor.aeron.OnDisposable;
import reactor.core.publisher.Mono;

public final class AeronServer {

  private final AeronOptions options;

  private AeronServer(AeronOptions options) {
    this.options = options;
  }

  /**
   * Creates {@link AeronServer}.
   *
   * @param resources aeron resources
   * @return new {@code AeronServer}
   */
  public static AeronServer create(AeronResources resources) {
    return new AeronServer(new AeronOptions().resources(resources));
  }

  /**
   * Binds {@link AeronServer}.
   *
   * @return mono handle of result
   */
  public Mono<? extends OnDisposable> bind() {
    return bind(s -> s);
  }

  /**
   * Binds {@link AeronServer} with options.
   *
   * @param op unary opearator for performing setup of options
   * @return mono handle of result
   */
  public Mono<? extends OnDisposable> bind(UnaryOperator<AeronOptions> op) {
    return Mono.defer(() -> new AeronServerHandler(op.apply(options)).start());
  }

  /**
   * Setting up {@link AeronServer} options.
   *
   * @param op unary opearator for performing setup of options
   * @return new {@code AeronServer} with applied options
   */
  public AeronServer options(UnaryOperator<AeronOptions> op) {
    return new AeronServer(op.apply(options));
  }

  /**
   * Attach IO handler to react on connected client.
   *
   * @param handler IO handler that can dispose underlying connection when {@link Publisher}
   *     terminates.
   * @return new {@code AeronServer} with handler
   */
  public AeronServer handle(Function<? super Connection, ? extends Publisher<Void>> handler) {
    return new AeronServer(options.handler(handler));
  }
}
