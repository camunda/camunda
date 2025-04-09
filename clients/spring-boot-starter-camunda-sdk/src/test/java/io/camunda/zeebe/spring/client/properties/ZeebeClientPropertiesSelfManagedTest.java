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

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.spring.client.properties.CamundaClientProperties.ClientMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = CamundaClientPropertiesTestConfig.class,
    properties = {"camunda.client.mode=self-managed", "camunda.client.zeebe.scope=zeebe-scope"})
public class ZeebeClientPropertiesSelfManagedTest {
  @Autowired CamundaClientProperties properties;

  @Test
  void shouldLoadDefaultsSelfManaged() {
    assertThat(properties.getMode()).isEqualTo(ClientMode.selfManaged);
    assertThat(properties.getZeebe().getGrpcAddress().toString())
        .isEqualTo("http://localhost:26500");
    assertThat(properties.getZeebe().getRestAddress().toString())
        .isEqualTo("http://localhost:8088");
    assertThat(properties.getZeebe().isPreferRestOverGrpc()).isEqualTo(false);
    assertThat(properties.getZeebe().getEnabled()).isEqualTo(true);
    assertThat(properties.getZeebe().getAudience()).isEqualTo("zeebe-api");
    assertThat(properties.getZeebe().getScope()).isEqualTo("zeebe-scope");
    assertThat(properties.getIdentity().getEnabled()).isEqualTo(true);
    assertThat(properties.getIdentity().getBaseUrl().toString()).isEqualTo("http://localhost:8084");
    assertThat(properties.getIdentity().getAudience()).isEqualTo("identity-api");
  }
}
