package org.camunda.optimize.dto.optimize;

public class ConnectionStatusDto {

  protected boolean isConnectedToEngine;
  protected boolean isConnectedToElasticsearch;

  /**
   * True if Optimize is connected to Camunda, false otherwise.
   */
  public boolean isConnectedToEngine() {
    return isConnectedToEngine;
  }

  public void setConnectedToEngine(boolean connectedToEngine) {
    isConnectedToEngine = connectedToEngine;
  }

  /**
   * True if Optimize is connected to the Elasticsearch, false otherwise.
   */
  public boolean isConnectedToElasticsearch() {
    return isConnectedToElasticsearch;
  }

  public void setConnectedToElasticsearch(boolean connectedToElasticsearch) {
    isConnectedToElasticsearch = connectedToElasticsearch;
  }
}
