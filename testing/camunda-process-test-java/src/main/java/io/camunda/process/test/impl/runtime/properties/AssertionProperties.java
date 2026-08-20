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

import io.camunda.process.test.impl.runtime.util.PropertiesUtil;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

public class AssertionProperties {

  public static final String PROPERTY_NAME_ASSERTION_TIMEOUT = "assertion.timeout";
  public static final String PROPERTY_NAME_ASSERTION_INTERVAL = "assertion.interval";

  private final Duration assertionTimeout;
  private final Duration assertionInterval;

  public AssertionProperties(final Properties properties) {
    assertionTimeout =
        PropertiesUtil.getPropertyOrNull(
            properties, PROPERTY_NAME_ASSERTION_TIMEOUT, Duration::parse);

    assertionInterval =
        PropertiesUtil.getPropertyOrNull(
            properties, PROPERTY_NAME_ASSERTION_INTERVAL, Duration::parse);
  }

  public Optional<Duration> getAssertionTimeout() {
    return Optional.ofNullable(assertionTimeout);
  }

  public Optional<Duration> getAssertionInterval() {
    return Optional.ofNullable(assertionInterval);
  }
}
