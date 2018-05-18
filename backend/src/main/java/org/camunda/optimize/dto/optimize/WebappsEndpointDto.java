package org.camunda.optimize.dto.optimize;

public class WebappsEndpointDto {

  private String endpoint;
  private String engineName;

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getEngineName() {
    return engineName;
  }

  public void setEngineName(String engineName) {
    this.engineName = engineName;
  }
}
