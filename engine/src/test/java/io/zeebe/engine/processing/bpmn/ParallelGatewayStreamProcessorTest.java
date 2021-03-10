/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.ParallelGateway;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class ParallelGatewayStreamProcessorTest {

  public static final String PROCESS_ID = "process";
  public static final DirectBuffer PROCESS_ID_BUFFER = BufferUtil.wrapString("process");

  public final StreamProcessorRule envRule = new StreamProcessorRule();
  public final ProcessInstanceStreamProcessorRule streamProcessorRule =
      new ProcessInstanceStreamProcessorRule(envRule);

  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  @Test
  public void shouldNotCompleteScopeWhenATokenWaitsAtAGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .sequenceFlowId("flowToJoin")
            .parallelGateway("join") // process should deadlock here
            .endEvent()
            .moveToNode("fork")
            .serviceTask("waitState", b -> b.zeebeJobType("type"))
            .sequenceFlowId("flowToEnd")
            .endEvent()
            .done();

    final ExclusiveGateway deadBranch = process.newInstance(ExclusiveGateway.class);
    final ParallelGateway join = process.getModelElementById("join");
    final SequenceFlow connectingFlow = process.newInstance(SequenceFlow.class);

    final Process processElement = process.getModelElementsByType(Process.class).iterator().next();
    processElement.addChildElement(deadBranch);
    processElement.addChildElement(connectingFlow);

    connectingFlow.setSource(deadBranch);
    deadBranch.getOutgoing().add(connectingFlow);
    connectingFlow.setTarget(join);
    join.getIncoming().add(connectingFlow);

    streamProcessorRule.deploy(process);

    streamProcessorRule.createProcessInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    streamProcessorRule.awaitElementInState(
        "flowToJoin", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);

    // when
    // waiting until the end event has been reached
    streamProcessorRule.completeFirstJob();

    // then
    // there should be no scope completing event
    final Optional<Record<ProcessInstanceRecord>> processCompleting =
        envRule
            .events()
            .onlyProcessInstanceRecords()
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETING)
            .filter(r -> PROCESS_ID_BUFFER.equals(r.getValue().getElementIdBuffer()))
            .findFirst();

    Assertions.assertThat(processCompleting).isNotPresent();
  }
}
