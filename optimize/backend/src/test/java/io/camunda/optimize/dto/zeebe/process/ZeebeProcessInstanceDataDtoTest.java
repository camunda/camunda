/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.junit.jupiter.api.Test;

class ZeebeProcessInstanceDataDtoTest {

  @Test
  void shouldDeserializeProcessInstanceRecordWithoutTagsInJson() throws Exception {
    // given — a record with no tags field at all
    final String json =
        """
        {
          "position": 1,
          "partitionId": 3,
          "intent": "ELEMENT_ACTIVATED",
          "valueType": "PROCESS_INSTANCE",
          "recordType": "EVENT",
          "value": {
            "bpmnProcessId": "myProcess",
            "version": 1,
            "processDefinitionKey": 42,
            "processInstanceKey": 99,
            "elementId": "Process_1",
            "flowScopeKey": -1,
            "bpmnElementType": "PROCESS",
            "parentProcessInstanceKey": -1,
            "parentElementInstanceKey": -1,
            "tenantId": "<default>"
          }
        }
        """;

    // when
    final ZeebeProcessInstanceRecordDto record =
        ObjectMapperFactory.OPTIMIZE_MAPPER.readValue(json, ZeebeProcessInstanceRecordDto.class);

    // then
    assertThat(record.getValue().getTags()).isEmpty();
  }

  @Test
  void shouldDeserializeProcessInstanceRecordWithTags() throws Exception {
    // given — a document that contains value.tags
    final String json =
        """
        {
          "position": 1,
          "partitionId": 3,
          "intent": "ELEMENT_ACTIVATED",
          "valueType": "PROCESS_INSTANCE",
          "recordType": "EVENT",
          "value": {
            "bpmnProcessId": "myProcess",
            "version": 1,
            "processDefinitionKey": 42,
            "processInstanceKey": 99,
            "elementId": "Process_1",
            "flowScopeKey": -1,
            "bpmnElementType": "PROCESS",
            "parentProcessInstanceKey": -1,
            "parentElementInstanceKey": -1,
            "tenantId": "<default>",
            "tags": ["tag1", "tag2"]
          }
        }
        """;

    // when
    final ZeebeProcessInstanceRecordDto record =
        ObjectMapperFactory.OPTIMIZE_MAPPER.readValue(json, ZeebeProcessInstanceRecordDto.class);

    // then
    assertThat(record.getValue().getTags()).containsExactlyInAnyOrder("tag1", "tag2");
  }

  @Test
  void shouldDeserializeProcessInstanceRecordWithEmptyTagsAndAllowMutation() throws Exception {
    // given
    final String json =
        """
        {
          "position": 1,
          "partitionId": 3,
          "intent": "ELEMENT_ACTIVATED",
          "valueType": "PROCESS_INSTANCE",
          "recordType": "EVENT",
          "value": {
            "bpmnProcessId": "myProcess",
            "version": 1,
            "processDefinitionKey": 42,
            "processInstanceKey": 99,
            "elementId": "Process_1",
            "flowScopeKey": -1,
            "bpmnElementType": "PROCESS",
            "parentProcessInstanceKey": -1,
            "parentElementInstanceKey": -1,
            "tenantId": "<default>",
            "tags": []
          }
        }
        """;

    // when
    final ZeebeProcessInstanceRecordDto record =
        ObjectMapperFactory.OPTIMIZE_MAPPER.readValue(json, ZeebeProcessInstanceRecordDto.class);

    // then — collection must be mutable; Set.of() would throw UnsupportedOperationException here
    assertThat(record.getValue().getTags()).isEmpty();
    record.getValue().getTags().add("tag3");
    assertThat(record.getValue().getTags()).containsExactly("tag3");
  }
}
