package org.camunda.operate.property;

/**
 * @author Svetlana Dorokhova.
 */
public class ElasticsearchProperties {

  private String clusterName= "elasticsearch";

  private String host = "localhost";

  private int port = 9300;

  private String dateFormat;

  private int insertBatchSize = 20;

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
}
