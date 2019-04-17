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
package io.zeebe.distributedlog.impl;

import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.StorageConfigurationManager;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* Used by DefaultDistributedLogstreamService to get the node specific objects/configuration */
public class LogstreamConfig {

  private static final Map<String, ServiceContainer> SERVICE_CONTAINERS = new ConcurrentHashMap<>();
  private static final Map<String, StorageConfigurationManager> CONFIGS = new ConcurrentHashMap<>();

  public static void putServiceContainer(String nodeId, ServiceContainer serviceContainer) {
    SERVICE_CONTAINERS.put(nodeId, serviceContainer);
  }

  public static ServiceContainer getServiceContainer(String nodeId) {
    return SERVICE_CONTAINERS.get(nodeId);
  }

  public static ActorFuture<StorageConfiguration> getConfig(String nodeId, int partitionId) {
    return CONFIGS.get(nodeId).createConfiguration(partitionId);
  }

  public static void putConfig(String nodeId, StorageConfigurationManager configManager) {
    CONFIGS.put(nodeId, configManager);
  }
}
