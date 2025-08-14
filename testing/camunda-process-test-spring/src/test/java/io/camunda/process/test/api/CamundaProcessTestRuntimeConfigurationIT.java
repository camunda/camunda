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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    classes = {LegacyCamundaProcessTestRuntimeConfigurationIT.class},
    properties = {
      "camunda.process-test.connectors-enabled=true",
      "camunda.process-test.camunda-docker-image-version=8.8.0-new",
      "camunda.process-test.camunda-docker-image-name=camunda/camunda-new",
      "io.camunda.process.test.camunda-docker-image-name=camunda/camunda-legacy",
    })
@Import({CamundaProcessTestAutoConfiguration.class})
public class CamundaProcessTestRuntimeConfigurationIT {

  @Autowired private CamundaProcessTestRuntimeConfiguration configuration;

  @Test
  public void shouldReadConfigurationWithNewPrefix() {
    assertThat(configuration.isConnectorsEnabled()).isTrue();
    assertThat(configuration.getCamundaDockerImageVersion()).isEqualTo("8.8.0-new");
    assertThat(configuration.getCamundaDockerImageName()).isEqualTo("camunda/camunda-new");
  }
}
