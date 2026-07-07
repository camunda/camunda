/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class ProcessCreatedV3ApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private ProcessCreatedV3Applier applier;

  @BeforeEach
  void setup() {
    applier = new ProcessCreatedV3Applier(processingState);
  }

  @Test
  void shouldStoreProcessAndRemainResolvable() {
    // given
    final long processDefinitionKey = 1L;
    final var model = Bpmn.createExecutableProcess("p").startEvent("s").endEvent("e").done();
    final var processRecord =
        new ProcessRecord()
            .setKey(processDefinitionKey)
            .setResourceName("p.bpmn")
            .setResource(wrapString(Bpmn.convertToString(model)))
            .setChecksum(wrapString("checksum"))
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setBpmnProcessId(wrapString("p"))
            .setVersion(1)
            .setDeploymentKey(2L);

    // when
    applier.applyState(processDefinitionKey, processRecord);

    // then — process is stored and its start event element resolves via the (default) transformer
    final var element =
        processingState
            .getProcessState()
            .getFlowElement(
                1L,
                TenantOwned.DEFAULT_TENANT_IDENTIFIER,
                BufferUtil.wrapString("s"),
                ExecutableFlowElement.class);
    assertThat(element).isNotNull();
  }
}
