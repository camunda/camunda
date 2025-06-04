package io.camunda.unifiedconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public class UnifiedConfiguration {
  private Camunda camunda;

  public Camunda getCamunda() {
    return camunda;
  }

  public void setCamunda(Camunda camunda) {
    this.camunda = camunda;
  }
}
