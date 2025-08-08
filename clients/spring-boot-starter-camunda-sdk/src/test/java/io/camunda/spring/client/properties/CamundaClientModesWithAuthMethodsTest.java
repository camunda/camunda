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
import io.camunda.spring.client.properties.CamundaClientAuthProperties.AuthMethod;
import io.camunda.spring.client.properties.CamundaClientProperties.ClientMode;
import java.net.URI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

public class CamundaClientModesWithAuthMethodsTest {
  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.mode=self-managed"})
  public class ImplicitNone {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBeNone() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.selfManaged);
      assertThat(properties.getGrpcAddress().toString()).isEqualTo("http://localhost:26500");
      assertThat(properties.getRestAddress().toString()).isEqualTo("http://localhost:8088");
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.none);
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.mode=self-managed", "camunda.client.auth.method=none"})
  public class ExplicitNone {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBeNone() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.selfManaged);
      assertThat(properties.getGrpcAddress().toString()).isEqualTo("http://localhost:26500");
      assertThat(properties.getRestAddress().toString()).isEqualTo("http://localhost:8088");
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.none);
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.mode=self-managed", "camunda.client.auth.username=basic"})
  public class ImplicitBasic {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBeBasic() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.selfManaged);
      assertThat(properties.getGrpcAddress().toString()).isEqualTo("http://localhost:26500");
      assertThat(properties.getRestAddress().toString()).isEqualTo("http://localhost:8088");
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.basic);
      assertThat(properties.getAuth().getUsername()).isEqualTo("basic");
      assertThat(properties.getAuth().getPassword()).isEqualTo("demo");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.mode=self-managed", "camunda.client.auth.method=basic"})
  public class ExplicitBasic {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBeBasic() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.selfManaged);
      assertThat(properties.getGrpcAddress().toString()).isEqualTo("http://localhost:26500");
      assertThat(properties.getRestAddress().toString()).isEqualTo("http://localhost:8088");
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.basic);
      assertThat(properties.getAuth().getUsername()).isEqualTo("demo");
      assertThat(properties.getAuth().getPassword()).isEqualTo("demo");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.mode=self-managed", "camunda.client.auth.client-id=basic"})
  public class ImplicitOidc {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBeOidc() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.selfManaged);
      assertThat(properties.getGrpcAddress().toString()).isEqualTo("http://localhost:26500");
      assertThat(properties.getRestAddress().toString()).isEqualTo("http://localhost:8088");
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.oidc);
      assertThat(properties.getAuth().getTokenUrl())
          .isEqualTo(
              URI.create(
                  "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token"));
      assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe-api");
      assertThat(properties.getAuth().getClientId()).isEqualTo("basic");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.mode=self-managed", "camunda.client.auth.method=oidc"})
  public class ExplicitOidc {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBeOidc() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.selfManaged);
      assertThat(properties.getGrpcAddress().toString()).isEqualTo("http://localhost:26500");
      assertThat(properties.getRestAddress().toString()).isEqualTo("http://localhost:8088");
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.oidc);
      assertThat(properties.getAuth().getTokenUrl())
          .isEqualTo(
              URI.create(
                  "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token"));
      assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe-api");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {
        "camunda.client.mode=saas",
        "camunda.client.cloud.cluster-id=my-cluster-id",
        "camunda.client.auth.method=oidc"
      })
  public class SaasExplicitOidc {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBeOidc() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.saas);
      assertThat(properties.getGrpcAddress().toString())
          .isEqualTo("https://my-cluster-id.bru-2.zeebe.camunda.io:443");
      assertThat(properties.getRestAddress().toString())
          .isEqualTo("https://bru-2.zeebe.camunda.io:443/my-cluster-id");
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.oidc);
      assertThat(properties.getAuth().getTokenUrl())
          .isEqualTo(URI.create("https://login.cloud.camunda.io/oauth/token"));
      assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe.camunda.io");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {
        "camunda.client.mode=saas",
        "camunda.client.cloud.cluster-id=my-cluster-id",
      })
  public class SaasImplicitOidc {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBeOidc() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.saas);
      assertThat(properties.getGrpcAddress().toString())
          .isEqualTo("https://my-cluster-id.bru-2.zeebe.camunda.io:443");
      assertThat(properties.getRestAddress().toString())
          .isEqualTo("https://bru-2.zeebe.camunda.io:443/my-cluster-id");
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.oidc);
      assertThat(properties.getAuth().getTokenUrl())
          .isEqualTo(URI.create("https://login.cloud.camunda.io/oauth/token"));
      assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe.camunda.io");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {
        "camunda.client.mode=saas",
        "camunda.client.cloud.cluster-id=my-cluster-id",
        "camunda.client.auth.method=basic"
      })
  public class SaasPreventBasic {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBeOidc() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.saas);
      assertThat(properties.getGrpcAddress().toString())
          .isEqualTo("https://my-cluster-id.bru-2.zeebe.camunda.io:443");
      assertThat(properties.getRestAddress().toString())
          .isEqualTo("https://bru-2.zeebe.camunda.io:443/my-cluster-id");
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.oidc);
      assertThat(properties.getAuth().getTokenUrl())
          .isEqualTo(URI.create("https://login.cloud.camunda.io/oauth/token"));
      assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe.camunda.io");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {
        "camunda.client.mode=saas",
        "camunda.client.cloud.cluster-id=my-cluster-id",
        "camunda.client.auth.method=none"
      })
  public class SaasPreventNone {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBeOidc() {
      assertThat(properties.getMode()).isEqualTo(ClientMode.saas);
      assertThat(properties.getGrpcAddress().toString())
          .isEqualTo("https://my-cluster-id.bru-2.zeebe.camunda.io:443");
      assertThat(properties.getRestAddress().toString())
          .isEqualTo("https://bru-2.zeebe.camunda.io:443/my-cluster-id");
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.oidc);
      assertThat(properties.getAuth().getTokenUrl())
          .isEqualTo(URI.create("https://login.cloud.camunda.io/oauth/token"));
      assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe.camunda.io");
    }
  }
}
