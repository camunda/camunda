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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

public class CamundaClientPropertiesTest {

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.auth.credentials-cache-path=/some/path"})
  class CredentialsCachePathConfigTest {
    @Autowired CamundaClientProperties camundaClientProperties;

    @Test
    void shouldApplyProperty() {
      assertThat(camundaClientProperties.getAuth().getCredentialsCachePath())
          .isEqualTo("/some/path");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"zeebe.client.cloud.credentials-cache-path=/some/path"})
  class LegacyCredentialsCachePathConfigTest {
    @Autowired CamundaClientProperties camundaClientProperties;

    @Test
    void shouldApplyProperty() {
      assertThat(camundaClientProperties.getAuth().getCredentialsCachePath())
          .isEqualTo("/some/path");
    }
  }
}
