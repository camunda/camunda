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
    properties = {
      "camunda.client.cluster-id=my-cluster-id",
      "camunda.client.region=bru-2",
      "camunda.client.mode=saas",
      "camunda.client.zeebe.scope=zeebe-scope"
    })
public class ZeebeClientPropertiesSaasTest {
  @Autowired CamundaClientProperties properties;

  @Test
  void shouldPopulateBaseUrlsForSaas() {
    assertThat(properties.getZeebe().getGrpcAddress().toString())
        .isEqualTo("https://my-cluster-id.bru-2.zeebe.camunda.io");
    assertThat(properties.getZeebe().getBaseUrl().toString())
        .isEqualTo("https://bru-2.zeebe.camunda.io/my-cluster-id");
    assertThat(properties.getZeebe().isPreferRestOverGrpc()).isEqualTo(false);
  }

  @Test
  void shouldLoadDefaultsSaas() {
    assertThat(properties.getMode()).isEqualTo(ClientMode.saas);
    assertThat(properties.getAuth().getIssuer())
        .isEqualTo("https://login.cloud.camunda.io/oauth/token");
    assertThat(properties.getZeebe().getEnabled()).isEqualTo(true);
    assertThat(properties.getZeebe().getAudience()).isEqualTo("zeebe.camunda.io");
    assertThat(properties.getZeebe().getScope()).isEqualTo("zeebe-scope");
    assertThat(properties.getIdentity().getEnabled()).isEqualTo(false);
  }
}
