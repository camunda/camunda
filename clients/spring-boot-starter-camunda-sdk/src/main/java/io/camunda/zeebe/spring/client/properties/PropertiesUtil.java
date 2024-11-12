/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
