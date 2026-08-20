/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransformerSlotStabilityTest {

  @Test
  void shouldKeepStableSlotIds() {
    // given — the frozen contract of slot ids (append new entries; never change existing ones)
    final Map<String, Integer> expected = new LinkedHashMap<>();
    expected.put("ERROR", 1);
    expected.put("ESCALATION", 2);
    expected.put("FLOW_ELEMENT_INSTANTIATION", 3);
    expected.put("MESSAGE", 4);
    expected.put("SIGNAL", 5);
    expected.put("CONDITIONAL", 6);
    expected.put("PROCESS", 7);
    expected.put("BOUNDARY_EVENT", 8);
    expected.put("BUSINESS_RULE_TASK", 9);
    expected.put("CALL_ACTIVITY", 10);
    expected.put("CATCH_EVENT", 11);
    expected.put("CONTEXT_PROCESS", 12);
    expected.put("END_EVENT", 13);
    expected.put("FLOW_NODE", 14);
    expected.put("SERVICE_TASK_JOB_WORKER", 15);
    expected.put("SEND_TASK_JOB_WORKER", 16);
    expected.put("RECEIVE_TASK", 17);
    expected.put("SCRIPT_TASK", 18);
    expected.put("SEQUENCE_FLOW", 19);
    expected.put("START_EVENT", 20);
    expected.put("USER_TASK", 21);
    expected.put("EVENT_BASED_GATEWAY", 22);
    expected.put("EXCLUSIVE_GATEWAY", 23);
    expected.put("INCLUSIVE_GATEWAY", 24);
    expected.put("INTERMEDIATE_CATCH_EVENT", 25);
    expected.put("SUB_PROCESS", 26);
    expected.put("INTERMEDIATE_THROW_EVENT", 27);
    expected.put("AD_HOC_SUB_PROCESS", 28);
    expected.put("MULTI_INSTANCE_ACTIVITY", 29);

    // when
    final Map<String, Integer> actual = new LinkedHashMap<>();
    for (final TransformerSlot slot : TransformerSlot.values()) {
      actual.put(slot.name(), slot.id());
    }

    // then — ids are unique and unchanged
    assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    assertThat(actual.values().stream().distinct().count())
        .describedAs("slot ids must be unique — never reuse or renumber")
        .isEqualTo((long) actual.size());
  }
}
