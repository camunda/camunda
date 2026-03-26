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
package io.camunda.process.test.impl.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.configuration.AssertionConfiguration;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.configuration.LegacyCamundaProcessTestRuntimeConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties({
  CamundaProcessTestRuntimeConfiguration.class,
  LegacyCamundaProcessTestRuntimeConfiguration.class
})
public class AssertionConfigurationTest {

  @Nested
  class DefaultConfig {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;

    @Test
    void shouldReturnEmpty() {
      final AssertionConfiguration assertionConfiguration = configuration.getAssertion();

      assertThat(assertionConfiguration).isNotNull();
      assertThat(assertionConfiguration.getTimeout()).isEmpty();
      assertThat(assertionConfiguration.getInterval()).isEmpty();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.assertion.timeout=PT1M",
        "camunda.process-test.assertion.interval=PT0.5S"
      })
  class ConfigureAssertion {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;

    @Test
    void shouldReturnValues() {
      final AssertionConfiguration assertionConfiguration = configuration.getAssertion();

      assertThat(assertionConfiguration).isNotNull();
      assertThat(assertionConfiguration.getTimeout()).hasValue(Duration.ofMinutes(1));
      assertThat(assertionConfiguration.getInterval()).hasValue(Duration.ofMillis(500));
    }
  }
}
