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
import java.net.URI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

public class CamundaClientAuthMethodsTest {
  @Nested
  @SpringBootTest(classes = CamundaClientPropertiesTestConfig.class)
  public class NoMode {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldWork() {}

    @Test
    void shouldDefaultToNone() {
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.none);
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = "camunda.client.auth.username=demo1")
  public class ImplicitBasicByUsername {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldLoadDefaultsBasic() {
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.basic);
      assertThat(properties.getAuth().getUsername()).isEqualTo("demo1");
      assertThat(properties.getAuth().getPassword()).isEqualTo("demo");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = "camunda.client.auth.password=demo1")
  public class ImplicitBasicByPassword {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldLoadDefaultsBasic() {
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.basic);
      assertThat(properties.getAuth().getUsername()).isEqualTo("demo");
      assertThat(properties.getAuth().getPassword()).isEqualTo("demo1");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.auth.client-id=some-client"})
  public class ImplicitOidcByClientId {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldLoadDefaultsBasic() {
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.oidc);
      assertThat(properties.getAuth().getTokenUrl())
          .isEqualTo(
              URI.create(
                  "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token"));
      assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe-api");
      assertThat(properties.getAuth().getClientId()).isEqualTo("some-client");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.auth.client-secret=some-secret"})
  public class ImplicitOidcByClientSecret {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldLoadDefaultsBasic() {
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.oidc);
      assertThat(properties.getAuth().getTokenUrl())
          .isEqualTo(
              URI.create(
                  "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token"));
      assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe-api");
      assertThat(properties.getAuth().getClientSecret()).isEqualTo("some-secret");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.auth.method=basic"})
  public class Basic {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldLoadDefaultsBasic() {
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.basic);
      assertThat(properties.getAuth().getUsername()).isEqualTo("demo");
      assertThat(properties.getAuth().getPassword()).isEqualTo("demo");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.auth.method=oidc"})
  public class Oidc {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldLoadDefaultsBasic() {
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
      properties = {"camunda.client.auth.method=none"})
  public class None {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldLoadDefaultsBasic() {
      assertThat(properties.getAuth().getMethod()).isEqualTo(AuthMethod.none);
    }
  }
}
