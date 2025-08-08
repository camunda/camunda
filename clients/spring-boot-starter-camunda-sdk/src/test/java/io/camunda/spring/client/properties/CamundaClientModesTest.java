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

import io.camunda.spring.client.CamundaClientPropertiesTestConfig;
import io.camunda.spring.client.properties.CamundaClientProperties.ClientMode;
import java.net.URI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

public class CamundaClientModesTest {
  @Nested
  @SpringBootTest(classes = CamundaClientPropertiesTestConfig.class)
  public class NoMode {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldWork() {}
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {
        "camunda.client.cloud.clusterId=my-cluster-id",
        "camunda.client.cloud.region=bru-2",
        "camunda.client.mode=saas",
      })
  public class Saas {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldPopulateBaseUrlsForSaas() {
      assertThat(properties.getGrpcAddress().toString())
          .isEqualTo("https://my-cluster-id.bru-2.zeebe.camunda.io:443");
      assertThat(properties.getRestAddress().toString())
          .isEqualTo("https://bru-2.zeebe.camunda.io:443/my-cluster-id");
    }

    @Test
    void shouldLoadDefaultsSaas() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.saas);
      assertThat(properties.getAuth().getTokenUrl())
          .isEqualTo(URI.create("https://login.cloud.camunda.io/oauth/token"));
      assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe.camunda.io");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {
        "camunda.client.mode=self-managed",
      })
  public class SelfManaged {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldLoadDefaultsSelfManaged() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.selfManaged);
      assertThat(properties.getGrpcAddress().toString()).isEqualTo("http://localhost:26500");
      assertThat(properties.getRestAddress().toString()).isEqualTo("http://localhost:8088");
    }
  }
}
