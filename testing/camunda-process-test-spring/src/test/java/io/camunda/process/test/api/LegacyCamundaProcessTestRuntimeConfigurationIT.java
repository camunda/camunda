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
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.configuration.CamundaProcessTestAutoConfiguration;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.configuration.LegacyCamundaProcessTestRuntimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    classes = {
      LegacyCamundaProcessTestRuntimeConfiguration.class,
      CamundaProcessTestAutoConfiguration.class,
      LegacyCamundaProcessTestRuntimeConfigurationIT.class,
    },
    properties = {
      "io.camunda.process.test.connectors-enabled=true",
      "io.camunda.process.test.camunda-docker-image-version=8.8.0-legacy",
      "io.camunda.process.test.camunda-docker-image-name=camunda/camunda-legacy"
    })
@Import({CamundaProcessTestAutoConfiguration.class})
public class LegacyCamundaProcessTestRuntimeConfigurationIT {

  @Autowired private CamundaProcessTestRuntimeConfiguration runtimeConfiguration;

  @Test
  public void usesLegacyConfiguration() {
    assertThat(runtimeConfiguration.isConnectorsEnabled()).isTrue();
    assertThat(runtimeConfiguration.getCamundaDockerImageVersion()).isEqualTo("8.8.0-legacy");
    assertThat(runtimeConfiguration.getCamundaDockerImageName())
        .isEqualTo("camunda/camunda-legacy");
  }
}
