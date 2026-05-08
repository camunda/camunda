/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.BpmnClusterConfigurationMapper;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Helper that emits a {@link ProcessInstanceCreationIntent#CREATE} command for a configuration
 * BPMN, seeded with the {@link ClusterConfiguration} as a JSON-friendly map process variable.
 *
 * <p>The variables are encoded as a one-key msgpack map: {@code {"clusterConfiguration": {...}}}.
 * Workers and the {@link BpmnClusterConfigurationMapper} share the same map representation so no
 * base64/proto decoding is required inside the BPMN.
 */
public final class BpmnInstanceStarter {

  static final String CLUSTER_CONFIG_VARIABLE = "clusterConfiguration";

  private BpmnInstanceStarter() {}

  /**
   * Append a new {@code ProcessInstanceCreationIntent.CREATE} command on the same stream.
   *
   * @param writers result-builder writers used by the surrounding processor
   * @param bpmnProcessId the latest deployed version of this id will be instantiated
   * @param initialConfig configuration carried as a process variable
   */
  public static void requestStart(
      final Writers writers, final String bpmnProcessId, final ClusterConfiguration initialConfig) {
    final ProcessInstanceCreationRecord create = new ProcessInstanceCreationRecord();
    create.setBpmnProcessId(bpmnProcessId);
    create.setVariables(new UnsafeBuffer(packInitialVariables(initialConfig)));
    writers.command().appendNewCommand(ProcessInstanceCreationIntent.CREATE, create);
  }

  private static byte[] packInitialVariables(final ClusterConfiguration cfg) {
    final Map<String, Object> configMap = BpmnClusterConfigurationMapper.toMap(cfg);
    final Map<String, Object> vars = Map.of(CLUSTER_CONFIG_VARIABLE, configMap);
    return MsgPackConverter.convertToMsgPack(vars);
  }
}
