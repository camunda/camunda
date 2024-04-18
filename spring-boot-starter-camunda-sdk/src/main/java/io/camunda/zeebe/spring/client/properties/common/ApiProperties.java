package io.camunda.zeebe.spring.client.properties.common;

import java.net.URL;

public class ApiProperties {
  private Boolean enabled;
  private URL baseUrl;
  private String audience;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public URL getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(URL baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }
}
