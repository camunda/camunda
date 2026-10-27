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

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyListOrEmpty;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrNull;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class CamundaClientWorkerProperties {
  public static final String PROPERTY_NAME_POLL_INTERVAL = "client.worker.pollInterval";
  public static final String PROPERTY_NAME_TIMEOUT = "client.worker.timeout";
  public static final String PROPERTY_NAME_MAX_JOBS_ACTIVE = "client.worker.maxJobsActive";
  public static final String PROPERTY_NAME_NAME = "client.worker.name";
  public static final String PROPERTY_NAME_TENANT_IDS = "client.worker.tenantIds";
  public static final String PROPERTY_NAME_STREAM_ENABLED = "client.worker.streamEnabled";

  private final String name;
  private final Duration timeout;
  private final Integer maxJobsActive;
  private final Duration pollInterval;
  private final List<String> tenantIds;
  private final Boolean streamEnabled;

  public CamundaClientWorkerProperties(final Properties properties) {
    name = getPropertyOrNull(properties, PROPERTY_NAME_NAME);
    timeout = getPropertyOrNull(properties, PROPERTY_NAME_TIMEOUT, Duration::parse);
    pollInterval = getPropertyOrNull(properties, PROPERTY_NAME_POLL_INTERVAL, Duration::parse);
    maxJobsActive = getPropertyOrNull(properties, PROPERTY_NAME_MAX_JOBS_ACTIVE, Integer::parseInt);
    streamEnabled =
        getPropertyOrNull(properties, PROPERTY_NAME_STREAM_ENABLED, Boolean::parseBoolean);
    tenantIds = getPropertyListOrEmpty(properties, PROPERTY_NAME_TENANT_IDS);
  }

  public String getName() {
    return name;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public Integer getMaxJobsActive() {
    return maxJobsActive;
  }

  public Duration getPollInterval() {
    return pollInterval;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public Boolean getStreamEnabled() {
    return streamEnabled;
  }
}
