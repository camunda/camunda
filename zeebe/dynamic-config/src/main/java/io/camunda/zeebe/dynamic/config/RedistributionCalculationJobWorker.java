/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.dynamic.config.api.PartitionReassignRequestTransformer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles the {@code redistribution-calculation} job type emitted by the scale operation BPMN
 * process. Computes partition join/leave/reconfigure operations for the new target cluster
 * membership using {@link PartitionReassignRequestTransformer} and returns them as {@code
 * workPayload.redistribution} for downstream BPMN sub-process iterations.
 */
public final class RedistributionCalculationJobWorker implements AutoCloseable {

  static final String JOB_TYPE = "redistribution-calculation";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final CamundaClient camundaClient;
  private JobWorker worker;

  public RedistributionCalculationJobWorker(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public void start() {
    worker = camundaClient.newWorker().jobType(JOB_TYPE).handler(this::handleJob).open();
  }

  @SuppressWarnings("unchecked")
  private void handleJob(final JobClient jobClient, final ActivatedJob job) {
    final var vars = job.getVariablesAsMap();

    try {
      final var membersToAdd = (List<Number>) vars.getOrDefault("membersToAdd", List.of());
      final var membersToRemove = (List<Number>) vars.getOrDefault("membersToRemove", List.of());
      final ClusterConfiguration currentConfig =
          BpmnClusterConfigurationMapper.fromMap(toConfigMap(vars.get("clusterConfiguration")));

      final Set<MemberId> newMembers = new HashSet<>(currentConfig.members().keySet());
      membersToAdd.forEach(id -> newMembers.add(MemberId.from(String.valueOf(id.intValue()))));
      membersToRemove.forEach(
          id -> newMembers.remove(MemberId.from(String.valueOf(id.intValue()))));

      final var result =
          new PartitionReassignRequestTransformer(newMembers).operations(currentConfig);

      if (result.isLeft()) {
        jobClient
            .newThrowErrorCommand(job.getKey())
            .errorCode("REDISTRIBUTION_CALCULATION_FAILED")
            .errorMessage(result.getLeft().getMessage())
            .send();
        return;
      }

      final var redistribution = toRedistributionList(result.get());
      jobClient
          .newCompleteCommand(job.getKey())
          .variable("workPayload", Map.of("redistribution", redistribution))
          .send();

    } catch (final Exception e) {
      jobClient
          .newThrowErrorCommand(job.getKey())
          .errorCode("REDISTRIBUTION_CALCULATION_FAILED")
          .errorMessage(e.getMessage())
          .send();
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toConfigMap(final Object raw) throws IOException {
    if (raw instanceof final Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    if (raw instanceof final String json) {
      return OBJECT_MAPPER.readValue(json, MAP_TYPE);
    }
    throw new IllegalArgumentException(
        "clusterConfiguration must be a JSON object or string, got: "
            + (raw == null ? "null" : raw.getClass().getName()));
  }

  /**
   * Groups the flat list of partition operations produced by {@link
   * PartitionReassignRequestTransformer} into per-partition entries expected by the BPMN
   * sub-process: {@code [{partitionId, join:[{brokerId, priority}], leaves:[{brokerId}],
   * reconfigure:[{brokerId, priority}]}]}.
   */
  private List<Map<String, Object>> toRedistributionList(
      final List<ClusterConfigurationChangeOperation> operations) {
    final Map<Integer, PartitionOps> byPartition = new LinkedHashMap<>();

    for (final var op : operations) {
      if (op instanceof final PartitionJoinOperation join) {
        byPartition
            .computeIfAbsent(join.partitionId(), PartitionOps::new)
            .join()
            .add(Map.of("brokerId", join.memberId().nodeIdx(), "priority", join.priority()));
      } else if (op instanceof final PartitionLeaveOperation leave) {
        byPartition
            .computeIfAbsent(leave.partitionId(), PartitionOps::new)
            .leaves()
            .add(Map.of("brokerId", leave.memberId().nodeIdx()));
      } else if (op instanceof final PartitionReconfigurePriorityOperation reconfig) {
        byPartition
            .computeIfAbsent(reconfig.partitionId(), PartitionOps::new)
            .reconfigure()
            .add(
                Map.of("brokerId", reconfig.memberId().nodeIdx(), "priority", reconfig.priority()));
      }
    }

    return byPartition.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            e -> {
              final Map<String, Object> entry = new HashMap<>();
              entry.put("partitionId", e.getKey());
              entry.put("join", e.getValue().join());
              entry.put("leaves", e.getValue().leaves());
              entry.put("reconfigure", e.getValue().reconfigure());
              return entry;
            })
        .toList();
  }

  @Override
  public void close() {
    if (worker != null) {
      worker.close();
    }
  }

  private record PartitionOps(
      int partitionId,
      List<Map<String, Object>> join,
      List<Map<String, Object>> leaves,
      List<Map<String, Object>> reconfigure) {
    PartitionOps(final int partitionId) {
      this(partitionId, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
  }
}
