/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.impl;

import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.StorageConfigurationManager;
import io.zeebe.distributedlog.restore.RestoreFactory;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* Used by DefaultDistributedLogstreamService to get the node specific objects/configuration */
public class LogstreamConfig {

  private static final Map<String, ServiceContainer> SERVICE_CONTAINERS = new ConcurrentHashMap<>();
  private static final Map<String, StorageConfigurationManager> CONFIGS = new ConcurrentHashMap<>();
  private static final Map<String, LogStream> LOGSTREAMS = new ConcurrentHashMap<>();
  private static final Map<String, RestoreFactory> RESTORE_FACTORIES = new ConcurrentHashMap<>();

  // A hack until we figure out https://github.com/zeebe-io/zeebe/issues/2484
  private static final Map<String, Boolean> RESTORING = new ConcurrentHashMap<>();

  public static void startRestore(String nodeId, int partitionId) {
    RESTORING.put(key(nodeId, partitionId), true);
  }

  public static void completeRestore(String nodeId, int partitionId) {
    RESTORING.put(key(nodeId, partitionId), false);
  }

  public static boolean isRestoring(String nodeId, int partitionId) {
    return RESTORING.computeIfAbsent(key(nodeId, partitionId), k -> false);
  }

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

  public static RestoreFactory getRestoreFactory(String nodeId) {
    return RESTORE_FACTORIES.get(nodeId);
  }

  public static void putRestoreFactory(String nodeId, RestoreFactory provider) {
    RESTORE_FACTORIES.put(nodeId, provider);
  }

  public static void removeRestoreFactory(String nodeId) {
    RESTORE_FACTORIES.remove(nodeId);
  }

  private static String key(String nodeId, int partitionId) {
    return String.format("%s-%d", nodeId, partitionId);
  }
}
