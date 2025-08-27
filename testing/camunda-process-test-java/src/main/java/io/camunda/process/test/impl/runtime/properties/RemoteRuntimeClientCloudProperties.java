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

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrNull;

import java.util.Properties;

public class RemoteRuntimeClientCloudProperties {
  public static final String PROPERTY_NAME_REGION = "remote.client.cloud.region";
  public static final String PROPERTY_NAME_CLUSTER_ID = "remote.client.cloud.clusterId";

  private final String region;
  private final String clusterId;

  public RemoteRuntimeClientCloudProperties(final Properties properties) {
    region = getPropertyOrNull(properties, PROPERTY_NAME_REGION);

    clusterId = getPropertyOrNull(properties, PROPERTY_NAME_CLUSTER_ID);
  }

  public String getRegion() {
    return region;
  }

  public String getClusterId() {
    return clusterId;
  }
}
