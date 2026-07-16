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
package io.camunda.process.test.impl.runtime.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class AssertionPropertiesTest {

  @Test
  void shouldReturnDefaults() {
    // given
    final Properties properties = new Properties();

    // when
    final AssertionProperties assertionProperties = new AssertionProperties(properties);

    // then
    assertThat(assertionProperties.getAssertionTimeout()).isEmpty();
    assertThat(assertionProperties.getAssertionInterval()).isEmpty();
    assertThat(assertionProperties.getQueryPageLimit()).isEmpty();
  }

  @Test
  void shouldReadAssertionTimeoutAndInterval() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("assertion.timeout", "PT30S");
    properties.setProperty("assertion.interval", "PT1S");

    // when
    final AssertionProperties assertionProperties = new AssertionProperties(properties);

    // then
    assertThat(assertionProperties.getAssertionTimeout()).contains(Duration.ofSeconds(30));
    assertThat(assertionProperties.getAssertionInterval()).contains(Duration.ofSeconds(1));
  }

  @Test
  void shouldReadQueryPageLimit() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("assertion.queryPageLimit", "500");

    // when
    final AssertionProperties assertionProperties = new AssertionProperties(properties);

    // then
    assertThat(assertionProperties.getQueryPageLimit()).contains(500);
  }

  @Test
  void shouldRejectInvalidQueryPageLimit() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("assertion.queryPageLimit", "not-a-number");

    // when / then
    assertThatThrownBy(() -> new AssertionProperties(properties))
        .isInstanceOf(NumberFormatException.class);
  }

  @Test
  void shouldTreatPlaceholderAsAbsent() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("assertion.timeout", "${ASSERTION_TIMEOUT}");
    properties.setProperty("assertion.interval", "${ASSERTION_INTERVAL}");
    properties.setProperty("assertion.queryPageLimit", "${QUERY_PAGE_LIMIT}");

    // when
    final AssertionProperties assertionProperties = new AssertionProperties(properties);

    // then
    assertThat(assertionProperties.getAssertionTimeout()).isEmpty();
    assertThat(assertionProperties.getAssertionInterval()).isEmpty();
    assertThat(assertionProperties.getQueryPageLimit()).isEmpty();
  }
}
