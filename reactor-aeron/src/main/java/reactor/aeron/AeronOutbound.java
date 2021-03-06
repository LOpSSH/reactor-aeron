package reactor.aeron;

import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Mono;

public interface AeronOutbound extends Publisher<Void> {

  /**
   * Send data to the peer, listen for any error on write and close on terminal signal
   * (complete|error).
   *
   * @param <B> abstract buffer type (comes from client code)
   * @param dataStream the dataStream publishing items to send
   * @param bufferHandler abstract buffer handler for {@link DirectBuffer} buffer
   * @return A new {@link AeronOutbound} to append further send. It will emit a complete signal upon
   *     successful sequence write or an error during write.
   */
  <B> AeronOutbound send(Publisher<B> dataStream, DirectBufferHandler<? super B> bufferHandler);

  /**
   * Send data to the peer, listen for any error on write and close on terminal signal
   * (complete|error).
   *
   * @param dataStream the dataStream publishing items to send
   * @return A new {@link AeronOutbound} to append further send. It will emit a complete signal upon
   *     successful sequence write or an error during write.
   */
  AeronOutbound send(Publisher<DirectBuffer> dataStream);

  /**
   * Send data to the peer, listen for any error on write and close on terminal signal
   * (complete|error).
   *
   * @param dataStream the dataStream publishing items to send
   * @return A new {@link AeronOutbound} to append further send. It will emit a complete signal upon
   *     successful sequence write or an error during write.
   */
  AeronOutbound sendBytes(Publisher<byte[]> dataStream);

  /**
   * Send data to the peer, listen for any error on write and close on terminal signal
   * (complete|error).
   *
   * @param dataStream the dataStream publishing items to send
   * @return A new {@link AeronOutbound} to append further send. It will emit a complete signal upon
   *     successful sequence write or an error during write.
   */
  AeronOutbound sendString(Publisher<String> dataStream);

  /**
   * Send data to the peer, listen for any error on write and close on terminal signal
   * (complete|error).
   *
   * @param dataStream the dataStream publishing items to send
   * @return A new {@link AeronOutbound} to append further send. It will emit a complete signal upon
   *     successful sequence write or an error during write.
   */
  AeronOutbound sendBuffer(Publisher<ByteBuffer> dataStream);

  /**
   * Obtain a {@link Mono} of pending outbound(s) write completion.
   *
   * @return a {@link Mono} of pending outbound(s) write completion
   */
  default Mono<Void> then() {
    return Mono.empty();
  }

  /**
   * Append a {@link Publisher} task such as a Mono and return a new {@link AeronOutbound} to
   * sequence further send.
   *
   * @param other the {@link Publisher} to subscribe to when this pending outbound {@link #then} is
   *     complete;
   * @return a new {@link AeronOutbound}
   */
  default AeronOutbound then(Publisher<Void> other) {
    return new AeronOutboundThen(this, other);
  }

  /**
   * Subscribe a {@code Void} subscriber to this outbound and trigger all eventual parent outbound
   * send.
   *
   * @param s the {@link Subscriber} to listen for send sequence completion/failure
   */
  @Override
  default void subscribe(Subscriber<? super Void> s) {
    then().subscribe(s);
  }
}
