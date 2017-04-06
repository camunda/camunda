package org.camunda.optimize.qa.performance.framework;

import org.elasticsearch.client.transport.TransportClient;

import java.util.Properties;

public class PerfTestConfiguration {

  private long maxServiceExecutionDuration;
  private int numberOfThreads;
  private int dataGenerationSize;

  private TransportClient client;
  private String optimizeIndex;
  private String eventType;
  private String branchAnalysisDataType;
  private String dateFormat;
  private String frequencyHeatMapEndpoint;
  private String durationHeatMapEndpoint;

  private String authorizationToken;

  public PerfTestConfiguration(Properties properties) {
    maxServiceExecutionDuration = Long.parseLong(properties.getProperty("test.heatmap.max.duration.ms", "1000"));
    numberOfThreads = Integer.parseInt(properties.getProperty("data.generation.numberOfThreads", "2"));
    dataGenerationSize = Integer.parseInt(properties.getProperty("data.generation.size", "1000000"));

    optimizeIndex = properties.getProperty("camunda.optimize.es.index", "optimize");
    eventType = properties.getProperty("camunda.optimize.es.event.type", "event");
    branchAnalysisDataType = properties.getProperty("camunda.optimize.es.branchAnalysisData.type", "branchAnalysisData");
    dateFormat = properties.getProperty("camunda.optimize.serialization.date.format", "yyyy-MM-dd'T'HH:mm:ss");
    frequencyHeatMapEndpoint = properties.getProperty("camunda.optimize.rest.heatmap.frequency",
      "http://localhost:8090/api/process-definition/heatmap/frequency");
    durationHeatMapEndpoint = properties.getProperty("camunda.optimize.rest.heatmap.duration",
      "http://localhost:8090/api/process-definition/heatmap/duration");
  }

  public int getNumberOfThreads() {
    return numberOfThreads;
  }

  public int getDataGenerationSize() {
    return dataGenerationSize;
  }

  public String getOptimizeIndex() {
    return optimizeIndex;
  }

  public String getEventType() {
    return eventType;
  }

  public String getBranchAnalysisDataType() {
    return branchAnalysisDataType;
  }

  public String getAuthorizationToken() {
    return authorizationToken;
  }

  public void setAuthorizationToken(String authorizationToken) {
    this.authorizationToken = authorizationToken;
  }

  public TransportClient getClient() {
    return client;
  }

  public void setClient(TransportClient client) {
    this.client = client;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public String getFrequencyHeatMapEndpoint() {
    return frequencyHeatMapEndpoint;
  }

  public String getDurationHeatMapEndpoint() {
    return durationHeatMapEndpoint;
  }

  public long getMaxServiceExecutionDuration() {
    return maxServiceExecutionDuration;
  }
}
