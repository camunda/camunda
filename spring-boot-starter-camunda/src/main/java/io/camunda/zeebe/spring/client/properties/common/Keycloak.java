package io.camunda.zeebe.spring.client.properties.common;

public class Keycloak {

  @Override
  public String toString() {
    return "Keycloak{"
        + "url='"
        + url
        + '\''
        + ", realm='"
        + realm
        + '\''
        + ", tokenUrl='"
        + tokenUrl
        + '\''
        + '}';
  }

  private String url;
  private String realm;
  private String tokenUrl;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getRealm() {
    return realm;
  }

  public void setRealm(String realm) {
    this.realm = realm;
  }

  public String getTokenUrl() {
    return tokenUrl;
  }

  public void setTokenUrl(String tokenUrl) {
    this.tokenUrl = tokenUrl;
  }
}
