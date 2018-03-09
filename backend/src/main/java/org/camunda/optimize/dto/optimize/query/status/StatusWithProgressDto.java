package org.camunda.optimize.dto.optimize.query.status;

public class StatusWithProgressDto {

  protected ConnectionStatusDto connectionStatus;
  protected long progress;

  public ConnectionStatusDto getConnectionStatus() {
    return connectionStatus;
  }

  public void setConnectionStatus(ConnectionStatusDto connectionStatus) {
    this.connectionStatus = connectionStatus;
  }

  public long getProgress() {
    return progress;
  }

  public void setProgress(long progress) {
    this.progress = progress;
  }
}
