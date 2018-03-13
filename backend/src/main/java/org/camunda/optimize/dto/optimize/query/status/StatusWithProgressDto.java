package org.camunda.optimize.dto.optimize.query.status;

import java.util.Map;

public class StatusWithProgressDto {

  protected ConnectionStatusDto connectionStatus;
  protected Map<String, Long> progress;

  public ConnectionStatusDto getConnectionStatus() {
    return connectionStatus;
  }

  public void setConnectionStatus(ConnectionStatusDto connectionStatus) {
    this.connectionStatus = connectionStatus;
  }

  public Map<String, Long> getProgress() {
    return progress;
  }

  public void setProgress(Map<String, Long> progress) {
    this.progress = progress;
  }
}
