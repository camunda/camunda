package org.camunda.optimize.dto.optimize.query.status;

import java.util.Map;

public class StatusWithProgressDto {

  protected ConnectionStatusDto connectionStatus;
  protected Map<String, Boolean> isImporting;

  public ConnectionStatusDto getConnectionStatus() {
    return connectionStatus;
  }

  public void setConnectionStatus(ConnectionStatusDto connectionStatus) {
    this.connectionStatus = connectionStatus;
  }

  public Map<String, Boolean> getIsImporting() {
    return isImporting;
  }

  public void setIsImporting(Map<String, Boolean> isImporting) {
    this.isImporting = isImporting;
  }
}
