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
package io.zeebe.gateway;

import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.util.TomlConfigurationReader;
import java.nio.file.Paths;

public class StandaloneGateway {

  public static void main(String args[]) throws Exception {
    final Gateway gateway;
    if (args.length >= 1) {
      gateway = createGatewayFromConfiguration(args);
    } else {
      gateway = createDefaultGateway();
    }

    gateway.listenAndServe();
  }

  private static Gateway createGatewayFromConfiguration(String[] args) {
    String configFileLocation = args[0];

    if (!Paths.get(configFileLocation).isAbsolute()) {
      configFileLocation =
          Paths.get(getBasePath(), configFileLocation).toAbsolutePath().normalize().toString();
    }

    final GatewayCfg gatewayCfg =
        TomlConfigurationReader.read(configFileLocation, GatewayCfg.class);

    return createGateway(gatewayCfg);
  }

  private static Gateway createDefaultGateway() {
    return createGateway(new GatewayCfg());
  }

  private static Gateway createGateway(GatewayCfg gatewayCfg) {
    gatewayCfg.init();
    return new Gateway(gatewayCfg);
  }

  private static String getBasePath() {
    String basePath = System.getProperty("basedir");

    if (basePath == null) {
      basePath = Paths.get(".").toAbsolutePath().normalize().toString();
    }

    return basePath;
  }
}
