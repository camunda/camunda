package io.camunda.zeebe.spring.client.properties;

import io.camunda.zeebe.spring.client.properties.common.*;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "common")
public class CommonConfigurationProperties extends Client {

  @Override
  public String toString() {
    return "CommonConfigurationProperties{" + "keycloak=" + keycloak + "} " + super.toString();
  }

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @NestedConfigurationProperty private Keycloak keycloak = new Keycloak();

  public Keycloak getKeycloak() {
    return keycloak;
  }

  public void setKeycloak(Keycloak keycloak) {
    this.keycloak = keycloak;
  }
}
