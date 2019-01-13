package reactor.aeron;

import io.aeron.Image;
import io.aeron.ImageFragmentAssembler;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.agrona.DirectBuffer;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Operators;

public final class DefaultAeronInbound implements AeronInbound {

  private static final int MAX_FRAGMENT_LIMIT = 4096;

  private static final AtomicLongFieldUpdater<DefaultAeronInbound> REQUESTED =
      AtomicLongFieldUpdater.newUpdater(DefaultAeronInbound.class, "requested");

  private static final AtomicReferenceFieldUpdater<DefaultAeronInbound, CoreSubscriber>
      DESTINATION_SUBSCRIBER =
          AtomicReferenceFieldUpdater.newUpdater(
              DefaultAeronInbound.class, CoreSubscriber.class, "destinationSubscriber");

  private final Image image;
  private final AeronEventLoop eventLoop;
  private final FluxReceive inbound = new FluxReceive();
  private final FragmentHandler fragmentHandler =
      new ImageFragmentAssembler(new InnerFragmentHandler());
  private final MessageSubscription subscription;

  private volatile long requested;
  private long produced;
  private volatile CoreSubscriber<? super ByteBuffer> destinationSubscriber;

  /**
   * Constructor.
   *
   * @param image image
   * @param eventLoop event loop
   * @param subscription subscription
   */
  public DefaultAeronInbound(
      Image image, AeronEventLoop eventLoop, MessageSubscription subscription) {
    this.image = image;
    this.eventLoop = eventLoop;
    this.subscription = subscription;
  }

  @Override
  public ByteBufferFlux receive() {
    return new ByteBufferFlux(inbound);
  }

  int poll() {
    int r = (int) Math.min(requested, MAX_FRAGMENT_LIMIT);
    int fragments = 0;
    if (r > 0) {
      fragments = image.poll(fragmentHandler, r);
      if (produced > 0) {
        Operators.produced(REQUESTED, this, produced);
        produced = 0;
      }
    }
    return fragments;
  }

  void close() {
    if (!eventLoop.inEventLoop()) {
      throw new IllegalStateException("Can only close aeron inbound from within event loop");
    }
    inbound.cancel();
  }

  void dispose() {
    eventLoop
        .disposeInbound(this)
        .subscribe(
            null,
            th -> {
              // no-op
            });
    if (subscription != null) {
      subscription.dispose();
    }
  }

  private class InnerFragmentHandler implements FragmentHandler {

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
      ByteBuffer dstBuffer = ByteBuffer.allocate(length);
      buffer.getBytes(offset, dstBuffer, length);
      dstBuffer.flip();

      CoreSubscriber<? super ByteBuffer> destination =
          DefaultAeronInbound.this.destinationSubscriber;

      // TODO check on cancel?
      destination.onNext(dstBuffer);
      produced++;
    }
  }

  private class FluxReceive extends Flux<ByteBuffer> implements Subscription {

    @Override
    public void request(long n) {
      Operators.addCap(REQUESTED, DefaultAeronInbound.this, n);
    }

    @Override
    public void cancel() {
      REQUESTED.set(DefaultAeronInbound.this, 0);
      // TODO think again whether re-subscribtion is allowed; or we have to should emit cancel
      // signal upper to some conneciton shutdown hook
      CoreSubscriber destination = DESTINATION_SUBSCRIBER.getAndSet(DefaultAeronInbound.this, null);
      if (destination != null) {
        destination.onComplete();
      }
    }

    @Override
    public void subscribe(CoreSubscriber<? super ByteBuffer> destinationSubscriber) {
      boolean destinationSet =
          DESTINATION_SUBSCRIBER.compareAndSet(
              DefaultAeronInbound.this, null, destinationSubscriber);
      if (destinationSet) {
        destinationSubscriber.onSubscribe(this);
      } else {
        Operators.error(destinationSubscriber, Exceptions.duplicateOnSubscribeException());
      }
    }
  }
}
