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

<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/properties/CamundaClientPropertiesSaasTest.java
import io.camunda.spring.client.properties.CamundaClientProperties.ClientMode;
=======
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties.ClientMode;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/properties/ZeebeClientPropertiesSaasTest.java
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = CamundaClientPropertiesTestConfig.class,
    properties = {
      "camunda.client.cloud.clusterId=my-cluster-id",
      "camunda.client.cloud.region=bru-2",
      "camunda.client.mode=saas",
      "camunda.client.auth.scope=zeebe-scope"
    })
public class CamundaClientPropertiesSaasTest {
  @Autowired CamundaClientProperties properties;

  @Test
  void shouldPopulateBaseUrlsForSaas() {
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/properties/CamundaClientPropertiesSaasTest.java
    assertThat(properties.getGrpcAddress().toString())
        .isEqualTo("https://my-cluster-id.bru-2.zeebe.camunda.io:443");
    assertThat(properties.getRestAddress().toString())
        .isEqualTo("https://bru-2.zeebe.camunda.io:443/my-cluster-id");
=======
    assertThat(properties.getZeebe().getGrpcAddress().toString())
        .isEqualTo("https://my-cluster-id.bru-2.zeebe.camunda.io:443");
    assertThat(properties.getZeebe().getRestAddress().toString())
        .isEqualTo("https://bru-2.zeebe.camunda.io:443/my-cluster-id");
    assertThat(properties.getZeebe().getPreferRestOverGrpc()).isEqualTo(false);
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/properties/ZeebeClientPropertiesSaasTest.java
  }

  @Test
  void shouldLoadDefaultsSaas() {
    assertThat(properties.getMode()).isEqualTo(ClientMode.saas);
    assertThat(properties.getAuth().getTokenUrl())
        .isEqualTo(URI.create("https://login.cloud.camunda.io/oauth/token"));
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/properties/CamundaClientPropertiesSaasTest.java
    assertThat(properties.getEnabled()).isEqualTo(true);
    assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe.camunda.io");
    assertThat(properties.getAuth().getScope()).isEqualTo("zeebe-scope");
=======
    assertThat(properties.getZeebe().getEnabled()).isEqualTo(true);
    assertThat(properties.getZeebe().getAudience()).isEqualTo("zeebe.camunda.io");
    assertThat(properties.getZeebe().getScope()).isEqualTo("zeebe-scope");
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/properties/ZeebeClientPropertiesSaasTest.java
  }
}
