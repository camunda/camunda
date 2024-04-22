package io.camunda.zeebe.spring.client.properties;

import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertiesUtil {
  private static final Logger LOG = LoggerFactory.getLogger(PropertiesUtil.class);

  public static String getZeebeGatewayAddress(final ZeebeClientConfigurationProperties properties) {
    final String connectionMode = properties.getConnectionMode();
    if (connectionMode != null && !connectionMode.isEmpty()) {
      LOG.info("Using connection mode '{}' to connect to Zeebe", connectionMode);
      if (CONNECTION_MODE_CLOUD.equalsIgnoreCase(connectionMode)) {
        return properties.getCloud().getGatewayAddress();
      } else if (CONNECTION_MODE_ADDRESS.equalsIgnoreCase(connectionMode)) {
        return properties.getBroker().getGatewayAddress();
      } else {
        throw new RuntimeException(
            "Value '"
                + connectionMode
                + "' for ConnectionMode is invalid, valid values are "
                + CONNECTION_MODE_CLOUD
                + " or "
                + CONNECTION_MODE_ADDRESS);
      }
    } else if (properties.getCloud().isConfigured()) {
      return properties.getCloud().getGatewayAddress();
    } else {
      return properties.getBroker().getGatewayAddress();
    }
  }
}
