package org.camunda.operate.property;


public class ElasticsearchProperties {

  public static final String WORKFLOW_INSTANCE_INDEX_NAME_DEFAULT = "workflow-instance";

  public static final String EVENT_INDEX_NAME_DEFAULT = "event";

  public static final String WORKFLOW_INDEX_NAME_DEFAULT = "workflow";

  private String clusterName= "elasticsearch";

  private String host = "localhost";

  private int port = 9300;

  private String dateFormat;

  private int insertBatchSize = 20;

  private String eventIndexName = EVENT_INDEX_NAME_DEFAULT;

  private String workflowInstanceIndexName = WORKFLOW_INSTANCE_INDEX_NAME_DEFAULT;

  private String workflowIndexName = WORKFLOW_INDEX_NAME_DEFAULT;

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public int getInsertBatchSize() {
    return insertBatchSize;
  }

  public void setInsertBatchSize(int insertBatchSize) {
    this.insertBatchSize = insertBatchSize;
  }

  public String getWorkflowInstanceIndexName() {
    return workflowInstanceIndexName;
  }

  public void setWorkflowInstanceIndexName(String workflowInstanceIndexName) {
    this.workflowInstanceIndexName = workflowInstanceIndexName;
  }

  public String getWorkflowIndexName() {
    return workflowIndexName;
  }

  public void setWorkflowIndexName(String workflowIndexName) {
    this.workflowIndexName = workflowIndexName;
  }

  public String getEventIndexName() {
    return eventIndexName;
  }

  public void setEventIndexName(String eventIndexName) {
    this.eventIndexName = eventIndexName;
  }
}
