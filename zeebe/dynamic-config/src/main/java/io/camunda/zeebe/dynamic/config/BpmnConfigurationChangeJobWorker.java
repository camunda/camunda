/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import java.util.Map;
import java.util.Optional;

public final class BpmnConfigurationChangeJobWorker implements AutoCloseable {

  private final CamundaClient camundaClient;
  private final MemberId memberId;
  private final ClusterConfigurationManagerImpl configManager;
  private final ConfigurationChangeAppliers changeAppliers;
  private JobWorker worker;

  public BpmnConfigurationChangeJobWorker(
      final CamundaClient camundaClient,
      final MemberId memberId,
      final ClusterConfigurationManagerImpl configManager,
      final ConfigurationChangeAppliers changeAppliers) {
    this.camundaClient = camundaClient;
    this.memberId = memberId;
    this.configManager = configManager;
    this.changeAppliers = changeAppliers;
  }

  public void start() {
    worker =
        camundaClient
            .newWorker()
            .jobType("config-change-" + memberId.id())
            .handler(this::handleJob)
            .open();
  }

  @SuppressWarnings("unchecked")
  private void handleJob(final JobClient jobClient, final ActivatedJob job) {
    final var vars = job.getVariablesAsMap();

    final ClusterConfiguration providedConfig;
    final ClusterConfigurationChangeOperation operation;
    try {
      providedConfig =
          BpmnClusterConfigurationMapper.fromMap(
              (Map<String, Object>) vars.get("clusterConfiguration"));
      operation = deserializeOperation(vars);
    } catch (final Exception e) {
      jobClient
          .newThrowErrorCommand(job.getKey())
          .errorCode("INVALID_JOB_VARIABLES")
          .errorMessage(e.getMessage())
          .send();
      return;
    }

    configManager
        .applyOperationDirectly(providedConfig, operation, changeAppliers)
        .onComplete(
            (updatedConfig, error) -> {
              if (error == null) {
                jobClient
                    .newCompleteCommand(job.getKey())
                    .variable(
                        "clusterConfiguration", BpmnClusterConfigurationMapper.toMap(updatedConfig))
                    .send();
              } else {
                jobClient
                    .newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(error.getMessage())
                    .send();
              }
            });
  }

  private ClusterConfigurationChangeOperation deserializeOperation(final Map<String, Object> vars) {
    final var type = (String) vars.get("operationType");
    return switch (type) {
      case "partition-join" -> {
        final int partitionId = ((Number) vars.get("partitionId")).intValue();
        final int priority = ((Number) vars.get("priority")).intValue();
        yield new PartitionJoinOperation(memberId, partitionId, priority);
      }
      case "partition-leave" -> {
        final int partitionId = ((Number) vars.get("partitionId")).intValue();
        final int minReplicas = ((Number) vars.get("minimumAllowedReplicas")).intValue();
        yield new PartitionLeaveOperation(memberId, partitionId, minReplicas);
      }
      case "partition-reconfigure-priority" -> {
        final int partitionId = ((Number) vars.get("partitionId")).intValue();
        final int priority = ((Number) vars.get("priority")).intValue();
        yield new PartitionReconfigurePriorityOperation(memberId, partitionId, priority);
      }
      case "member-join" -> new MemberJoinOperation(memberId);
      case "member-leave" -> new MemberLeaveOperation(memberId);
      case "member-remove" -> {
        final var memberToRemove = MemberId.from((String) vars.get("memberToRemove"));
        yield new MemberRemoveOperation(memberId, memberToRemove);
      }
      case "partition-enable-exporter" -> {
        final int partitionId = ((Number) vars.get("partitionId")).intValue();
        final String exporterId = (String) vars.get("exporterId");
        final var initializeFrom = Optional.ofNullable((String) vars.get("initializeFrom"));
        yield new PartitionEnableExporterOperation(
            memberId, partitionId, exporterId, initializeFrom);
      }
      case "partition-disable-exporter" -> {
        final int partitionId = ((Number) vars.get("partitionId")).intValue();
        final String exporterId = (String) vars.get("exporterId");
        yield new PartitionDisableExporterOperation(memberId, partitionId, exporterId);
      }
      default -> throw new IllegalArgumentException("Unknown operationType: " + type);
    };
  }

  @Override
  public void close() {
    if (worker != null) {
      worker.close();
    }
  }
}
