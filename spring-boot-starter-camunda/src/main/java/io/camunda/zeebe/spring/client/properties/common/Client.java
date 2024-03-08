package io.camunda.zeebe.spring.client.properties.common;

public class Client {

  @Override
  public String toString() {
    return "Client{"
        + "clientId='"
        + "***"
        + '\''
        + ", clientSecret='"
        + "***"
        + '\''
        + ", username='"
        + "***"
        + '\''
        + ", password='"
        + "***"
        + '\''
        + ", enabled="
        + enabled
        + ", url='"
        + url
        + '\''
        + ", authUrl='"
        + authUrl
        + '\''
        + ", baseUrl='"
        + baseUrl
        + '\''
        + '}';
  }

  private String clientId;
  private String clientSecret;
  private String username;
  private String password;
  private Boolean enabled = false;
  private String url;
  private String authUrl;
  private String baseUrl;

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getAuthUrl() {
    return authUrl;
  }

  public void setAuthUrl(String authUrl) {
    this.authUrl = authUrl;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}
