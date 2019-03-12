package reactor.aeron.pure.archive;

import io.aeron.Aeron;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.ThreadingMode;
import java.time.Duration;
import reactor.aeron.Configurations;
import reactor.core.publisher.Flux;

public class SubClient {

  private static final String CHANNEL =
      new ChannelUriStringBuilder()
          .controlEndpoint(Broker.BROKER_REPLAY_ENDPOINT)
          .controlMode(CommonContext.MDC_CONTROL_MODE_DYNAMIC)
          .sessionId(787570648 /*todo NOTICE: always need to change!!!*/)
          .reliable(Boolean.TRUE)
          .media(CommonContext.UDP_MEDIA)
          .build();
  private static final int STREAM_ID = Broker.BROKER_REPLAY_STREAM_ID;

  public static void main(String[] args) {
    String aeronDirName = Utils.tmpFileName("aeron");

    try (MediaDriver mediaDriver =
            MediaDriver.launch(
                new Context()
                    .threadingMode(ThreadingMode.SHARED)
                    // .spiesSimulateConnection(false)
                    .errorHandler(Throwable::printStackTrace)
                    .aeronDirectoryName(aeronDirName)
                    .dirDeleteOnStart(true));
        Aeron aeron =
            Aeron.connect(
                new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName()));
        Subscription subscription =
            aeron.addSubscription(
                CHANNEL,
                STREAM_ID,
                Configurations::printAvailableImage,
                Configurations::printUnavailableImage)) {

      System.out.println("Created subscription: " + CHANNEL + ", streamId: " + STREAM_ID);

      Flux.interval(Duration.ofMillis(100))
          .doOnNext(
              i ->
                  subscription.poll(
                      (buffer, offset, length, header) -> {
                        final byte[] data = new byte[length];
                        buffer.getBytes(offset, data);

                        System.out.println(
                            String.format(
                                "Message to stream %d from session %d (%d@%d) <<%s>>",
                                STREAM_ID, header.sessionId(), length, offset, new String(data)));
                      },
                      10))
          .blockLast();

    } finally {
      Utils.removeFile(aeronDirName);
    }
  }
}
