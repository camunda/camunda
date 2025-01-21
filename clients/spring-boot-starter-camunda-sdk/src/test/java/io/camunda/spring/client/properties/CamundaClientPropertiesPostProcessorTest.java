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

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = CamundaClientPropertiesTestConfig.class)
public class CamundaClientPropertiesPostProcessorTest {
  @SpringBootTest(
      properties = {
        "zeebe.client.broker.grpc-address=http://legacy:26500",
        "camunda.client.zeebe.grpc-address=http://newer:26500",
        "zeebe.client.requestTimeout=PT1M",
        "camunda.client.tenant-ids=<default>, another one"
      })
  @Nested
  class BehaviourTest {
    @Autowired CamundaClientProperties camundaClientProperties;

    @Test
    void shouldPreferNewerProperties() {
      assertThat(camundaClientProperties.getGrpcAddress())
          .isEqualTo(URI.create("http://newer:26500"));
    }

    @Test
    void shouldUseRelaxedPropertyBinding() {
      assertThat(camundaClientProperties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldMapLists() {
      assertThat(camundaClientProperties.getWorker().getDefaults().getTenantIds())
          .contains("<default>", "another one");
    }
  }

  @SpringBootTest(
      properties = {
        "zeebe.client.worker.override.foo.name=bar",
        "camunda.client.worker.override.custom.max-jobs-active=10",
        "zeebe.client.worker.override.custom.max-jobs-active=8",
        "camunda.client.worker.override.third.stream-enabled=true",
        "camunda.client.zeebe.override.third.stream-enabled=false"
      })
  @Nested
  class OverrideMappingTest {
    @Autowired CamundaClientProperties camundaClientProperties;

    @Test
    void shouldMapLegacyProperties() {
      assertThat(camundaClientProperties.getWorker().getOverride().get("foo").getName())
          .isEqualTo("bar");
    }

    @Test
    void shouldPreferNewerProperties() {
      assertThat(camundaClientProperties.getWorker().getOverride().get("custom").getMaxJobsActive())
          .isEqualTo(10);
      assertThat(camundaClientProperties.getWorker().getOverride().get("third").getStreamEnabled())
          .isTrue();
    }
  }
  // TODO add more tests to verify that all properties are mapped accordingly
}
