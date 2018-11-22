package io.aeron.driver;

import static io.aeron.driver.Configuration.IMAGE_LIVENESS_TIMEOUT_NS;

public class AeronResourcesConfig {

  public static final boolean DELETE_AERON_DIR_ON_START = true;
  public static final ThreadingMode THREADING_MODE = ThreadingMode.DEDICATED;

  private final ThreadingMode threadingMode;
  private final boolean dirDeleteOnStart;
  private final long imageLivenessTimeoutNs;

  private AeronResourcesConfig(Builder builder) {
    this.threadingMode = builder.threadingMode;
    this.dirDeleteOnStart = builder.dirDeleteOnStart;
    this.imageLivenessTimeoutNs = builder.imageLivenessTimeoutNs;
  }

  public static AeronResourcesConfig defaultConfig() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isDirDeleteOnStart() {
    return dirDeleteOnStart;
  }

  public ThreadingMode threadingMode() {
    return threadingMode;
  }

  public long imageLivenessTimeoutNs() {
    return imageLivenessTimeoutNs;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("AeronResourcesConfig{");
    sb.append(", threadingMode=").append(threadingMode);
    sb.append(", dirDeleteOnStart=").append(dirDeleteOnStart);
    sb.append(", imageLivenessTimeoutNs=").append(imageLivenessTimeoutNs);
    sb.append('}');
    return sb.toString();
  }

  public static class Builder {

    private long imageLivenessTimeoutNs = IMAGE_LIVENESS_TIMEOUT_NS;
    private ThreadingMode threadingMode = THREADING_MODE;
    private boolean dirDeleteOnStart = DELETE_AERON_DIR_ON_START;

    private Builder() {}

    public Builder useThreadModeInvoker() {
      threadingMode = ThreadingMode.INVOKER;
      return this;
    }

    public Builder useThreadModeShared() {
      threadingMode = ThreadingMode.SHARED;
      return this;
    }

    public Builder useThreadModeSharedNetwork() {
      threadingMode = ThreadingMode.SHARED_NETWORK;
      return this;
    }

    public Builder useThreadModeDedicated() {
      threadingMode = ThreadingMode.DEDICATED;
      return this;
    }

    public Builder dirDeleteOnStart(boolean dirDeleteOnStart) {
      this.dirDeleteOnStart = dirDeleteOnStart;
      return this;
    }

    public Builder imageLivenessTimeoutNs(long imageLivenessTimeoutNs) {
      this.imageLivenessTimeoutNs = imageLivenessTimeoutNs;
      return this;
    }

    public AeronResourcesConfig build() {
      return new AeronResourcesConfig(this);
    }
  }
}
