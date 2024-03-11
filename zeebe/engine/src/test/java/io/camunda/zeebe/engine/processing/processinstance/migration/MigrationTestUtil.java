/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;

public class MigrationTestUtil {

  protected static long extractProcessDefinitionKeyByProcessId(
      final Record<DeploymentRecordValue> deployment, final String processId) {
    return deployment.getValue().getProcessesMetadata().stream()
        .filter(p -> p.getBpmnProcessId().equals(processId))
        .findAny()
        .orElseThrow()
        .getProcessDefinitionKey();
  }
}
