/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class MultiProcessZeebeImportIT extends OperateZeebeSearchAbstractIT {

  @Autowired
  @Qualifier("operateProcessIndex")
  private ProcessIndex processIndex;

  @Test
  public void shouldReadFromMultiProcessDiagram() throws IOException {

    // given
    final String bpmnFile = "multi-process.bpmn";
    operateTester.deployProcessAndWait(bpmnFile);

    // when
    final List<ProcessEntity> processEntities =
        testSearchRepository.searchAll(processIndex.getAlias(), ProcessEntity.class);
    final ProcessEntity process1 =
        processEntities.stream()
            .filter(x -> "process1".equals(x.getBpmnProcessId()))
            .findAny()
            .orElse(null);
    final ProcessEntity process2 =
        processEntities.stream()
            .filter(x -> "process2".equals(x.getBpmnProcessId()))
            .findAny()
            .orElse(null);

    // then
    assertThat(processEntities).hasSize(2);

    assertThat(process1).isNotNull();
    assertThat(process1.getName()).isEqualTo("Process 1");
    assertThat(process1.getResourceName()).isEqualTo(bpmnFile);
    assertThat(process1.getFlowNodes())
        .extracting("id")
        .containsExactlyInAnyOrder("startEvent1", "task1", "endEvent1");
    assertThat(process1.getFlowNodes())
        .extracting("name")
        .containsExactlyInAnyOrder("Start Event 1", "Task 1", "End Event 1");

    assertThat(process2).isNotNull();
    assertThat(process2.getName()).isEqualTo("Process 2");
    assertThat(process2.getResourceName()).isEqualTo(bpmnFile);
    assertThat(process2.getFlowNodes())
        .extracting("id")
        .containsExactlyInAnyOrder("startEvent2", "task2", "endEvent2");
    assertThat(process2.getFlowNodes())
        .extracting("name")
        .containsExactlyInAnyOrder("Start Event 2", "Task 2", "End Event 2");
  }
}
