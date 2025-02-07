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
package io.camunda.spring.client.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.spring.client.properties.CamundaClientProperties.ClientMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = CamundaClientPropertiesTestConfig.class,
    properties = {
      "camunda.client.mode=self-managed",
      "camunda.client.auth.scope=zeebe-scope",
    })
public class CamundaClientPropertiesSelfManagedTest {
  @Autowired CamundaClientProperties properties;

  @Test
  void shouldLoadDefaultsSelfManaged() {
    assertThat(properties.getMode()).isEqualTo(ClientMode.selfManaged);
    assertThat(properties.getGrpcAddress().toString()).isEqualTo("http://localhost:26500");
    assertThat(properties.getRestAddress().toString()).isEqualTo("http://localhost:8086");
    assertThat(properties.getEnabled()).isEqualTo(true);
    assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe-api");
    assertThat(properties.getAuth().getScope()).isEqualTo("zeebe-scope");
  }
}
