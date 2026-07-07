/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformerSlot;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class DbProcessStateTransformerVersionTest {

  private MutableProcessingState processingState;

  @Test
  void shouldResolveFlowElementForProcessWithoutStoredVersions() {
    // given — legacy process: no transformer-version entries in the ProcessRecord
    final var model = Bpmn.createExecutableProcess("legacy").startEvent("s").endEvent("e").done();
    final var record = new ProcessRecord();
    record
        .setResourceName("legacy.bpmn")
        .setResource(wrapString(Bpmn.convertToString(model)))
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .setBpmnProcessId(wrapString("legacy"))
        .setVersion(1)
        .setChecksum(wrapString("checksum"));
    final var processState = processingState.getProcessState();
    processState.putProcess(7L, record);

    // when
    final var element =
        processState.getFlowElement(
            7L,
            TenantOwned.DEFAULT_TENANT_IDENTIFIER,
            wrapString("s"),
            ExecutableFlowElement.class);

    // then — default (v1) pipeline resolves the element
    assertThat(element).isNotNull();
  }

  @Test
  void shouldResolveFlowElementForProcessWithOnlyDefaultVersions() {
    // given — process whose version map contains only default (v1) entries embedded in the record
    final var model1 = Bpmn.createExecutableProcess("p1").startEvent("s1").endEvent("e1").done();
    final var processState = processingState.getProcessState();
    final var record1 = new ProcessRecord();
    record1
        .setResourceName("p1.bpmn")
        .setResource(wrapString(Bpmn.convertToString(model1)))
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .setBpmnProcessId(wrapString("p1"))
        .setVersion(1)
        .setChecksum(wrapString("checksum1"))
        .setTransformerVersions(Map.of(TransformerSlot.ERROR.id(), 1));
    processState.putProcess(1L, record1);

    // when
    processState.clearCache();

    // then — default pipeline still resolves the element
    assertThat(
            processState.getFlowElement(
                1L,
                TenantOwned.DEFAULT_TENANT_IDENTIFIER,
                wrapString("s1"),
                ExecutableFlowElement.class))
        .isNotNull();
  }

  @Test
  void shouldThrowWhenPinnedVersionMissingFromCatalog() {
    // given — a process whose record embeds a non-default slot version with no matching factory
    final var model2 = Bpmn.createExecutableProcess("p2").startEvent("s2").endEvent("e2").done();
    final var processState = processingState.getProcessState();
    final var record2 = new ProcessRecord();
    record2
        .setResourceName("p2.bpmn")
        .setResource(wrapString(Bpmn.convertToString(model2)))
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .setBpmnProcessId(wrapString("p2"))
        .setVersion(1)
        .setChecksum(wrapString("checksum2"))
        .setTransformerVersions(Map.of(TransformerSlot.END_EVENT.id(), 2));
    processState.putProcess(2L, record2);

    // when / then — getFlowElement triggers cache-miss → reads embedded v2 for END_EVENT → no
    // factory registered → must fail fast rather than silently fall back to v1
    processState.clearCache();
    assertThatThrownBy(
            () ->
                processState.getFlowElement(
                    2L,
                    TenantOwned.DEFAULT_TENANT_IDENTIFIER,
                    wrapString("s2"),
                    ExecutableFlowElement.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("END_EVENT")
        .hasMessageContaining("version 2");
  }
}
