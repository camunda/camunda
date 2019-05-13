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
import io.zeebe.distributedlog.restore.PartitionLeaderElectionController;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreClientFactory;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/* Used by DefaultDistributedLogstreamService to get the node specific objects/configuration */
public class LogstreamConfig {

  private static final Map<String, ServiceContainer> SERVICE_CONTAINERS = new ConcurrentHashMap<>();
  private static final Map<String, StorageConfigurationManager> CONFIGS = new ConcurrentHashMap<>();
  private static final Map<String, LogStream> LOGSTREAMS = new ConcurrentHashMap<>();
  private static final Map<String, RestoreClientFactory> RESTORE_CLIENT_FACTORIES =
      new ConcurrentHashMap<>();
  private static final Map<String, CompletableFuture<PartitionLeaderElectionController>>
      LEADER_ELECTION_CONTROLLERS = new ConcurrentHashMap<>();

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

  public static void putLogStream(String nodeId, int partitionId, LogStream logstream) {
    LOGSTREAMS.put(key(nodeId, partitionId), logstream);
  }

  public static LogStream getLogStream(String nodeId, int partitionId) {
    return LOGSTREAMS.get(key(nodeId, partitionId));
  }

  public static RestoreClient getRestoreClient(String nodeId, int partitionId) {
    return RESTORE_CLIENT_FACTORIES.get(nodeId).createClient(partitionId);
  }

  public static void putRestoreClientFactory(String nodeId, RestoreClientFactory provider) {
    RESTORE_CLIENT_FACTORIES.put(nodeId, provider);
  }

  public static CompletableFuture<PartitionLeaderElectionController> getLeaderElectionController(
      String nodeId, int partitionId) {
    return LEADER_ELECTION_CONTROLLERS.computeIfAbsent(
        key(nodeId, partitionId), k -> new CompletableFuture<>());
  }

  public static void putLeaderElectionController(
      String nodeId, int partitionId, PartitionLeaderElectionController controller) {
    getLeaderElectionController(nodeId, partitionId).complete(controller);
  }

  public static void removeLeaderElectionController(String nodeId, int partitionId) {
    LEADER_ELECTION_CONTROLLERS.remove(key(nodeId, partitionId));
  }

  private static String key(String nodeId, int partitionId) {
    return String.format("%s-%d", nodeId, partitionId);
  }
}
