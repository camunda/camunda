package org.camunda.optimize.service.util.configuration;

public class ElasticsearchConnectionNodeConfiguration {

  private String host;
  private Integer tcpPort;
  private Integer httpPort;

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public Integer getTcpPort() {
    return tcpPort;
  }

  public void setTcpPort(Integer tcpPort) {
    this.tcpPort = tcpPort;
  }

  public Integer getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(Integer httpPort) {
    this.httpPort = httpPort;
  }
}
