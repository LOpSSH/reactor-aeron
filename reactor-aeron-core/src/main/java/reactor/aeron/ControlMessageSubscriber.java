package reactor.aeron;

import java.util.UUID;

public interface ControlMessageSubscriber extends PollerSubscriber {

  void onConnect(
      UUID connectRequestId,
      String clientChannel,
      int clientControlStreamId,
      int clientSessionStreamId);

  void onConnectAck(UUID connectRequestId, long sessionId, int serverSessionStreamId);

  void onHeartbeat(long sessionId);

  void onComplete(long sessionId);
}