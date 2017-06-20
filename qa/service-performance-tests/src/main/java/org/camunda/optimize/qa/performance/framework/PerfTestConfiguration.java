package org.camunda.optimize.qa.performance.framework;

import org.elasticsearch.client.Client;

import java.util.Properties;

public class PerfTestConfiguration {

  private long maxServiceExecutionDuration;
  private int numberOfThreads;
  private int dataGenerationSize;

  private Client client;
  private String optimizeIndex;
  private String processInstanceType;
  private String dateFormat;
  private String frequencyHeatMapEndpoint;
  private String durationHeatMapEndpoint;

  private String authorizationToken;

  public PerfTestConfiguration(Properties properties) {
    maxServiceExecutionDuration = Long.parseLong(properties.getProperty("test.heatmap.max.duration.ms", "1000"));
    numberOfThreads = Integer.parseInt(properties.getProperty("data.generation.numberOfThreads", "2"));
    dataGenerationSize = Integer.parseInt(properties.getProperty("data.generation.size", "500000"));

    optimizeIndex = properties.getProperty("camunda.optimize.es.index", "optimize");
    processInstanceType = properties.getProperty("camunda.optimize.es.process.instance.type", "process-instance");
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

  public String getProcessInstanceType() {
    return processInstanceType;
  }

  public String getAuthorizationToken() {
    return authorizationToken;
  }

  public void setAuthorizationToken(String authorizationToken) {
    this.authorizationToken = authorizationToken;
  }

  public Client getClient() {
    return client;
  }

  public void setClient(Client client) {
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
