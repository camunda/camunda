/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import org.junit.jupiter.api.Test;

class AgentRunEnrichmentReaderESTest {

  @Test
  void shouldReadOnlyTheProcessInstanceFieldsEnrichmentUses() {
    // when
    final MultiGetOperation get =
        AgentRunEnrichmentReaderES.processInstanceGet("optimize-process-instance-loan", "123");

    // then
    assertThat(get.index()).isEqualTo("optimize-process-instance-loan");
    assertThat(get.id()).isEqualTo("123");
    assertThat(get.source().filter().includes())
        .containsExactlyInAnyOrder(
            "processDefinitionKey",
            "state",
            "endDate",
            "flowNodeInstances.flowNodeInstanceId",
            "flowNodeInstances.flowNodeId",
            "flowNodeInstances.flowNodeType",
            "flowNodeInstances.startDate",
            "flowNodeInstances.endDate",
            "flowNodeInstances.totalDurationInMs",
            "incidents.activityId",
            "incidents.incidentType",
            "incidents.incidentMessage",
            "incidents.createTime");
  }
}
