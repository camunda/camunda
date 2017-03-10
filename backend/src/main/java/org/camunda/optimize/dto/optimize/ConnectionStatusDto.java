package org.camunda.optimize.dto.optimize;

public class ConnectionStatusDto {

  private boolean isConnectedToEngine;
  private boolean isConnectedToElasticsearch;

  public boolean isConnectedToEngine() {
    return isConnectedToEngine;
  }

  public void setConnectedToEngine(boolean connectedToEngine) {
    isConnectedToEngine = connectedToEngine;
  }

  public boolean isConnectedToElasticsearch() {
    return isConnectedToElasticsearch;
  }

  public void setConnectedToElasticsearch(boolean connectedToElasticsearch) {
    isConnectedToElasticsearch = connectedToElasticsearch;
  }
}
