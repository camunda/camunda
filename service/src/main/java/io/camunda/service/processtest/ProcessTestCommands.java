/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.processtest;

import io.camunda.zeebe.engine.inmemory.CommandRecord;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class ProcessTestCommands {

  public static CommandRecord createProcessInstance(
      final String processId, final String variables) {
    final ProcessInstanceCreationRecord command = new ProcessInstanceCreationRecord();
    command.setBpmnProcessId(processId).setVariables(transformVariables(variables));

    return new CommandRecord(
        ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATE, command);
  }

  public static CommandRecord deployResources(final String name, final String bpmnXml) {
    final DeploymentRecord command = new DeploymentRecord();

    command.resources().add().setResourceName(name).setResource(BufferUtil.wrapString(bpmnXml));

    return new CommandRecord(ValueType.DEPLOYMENT, DeploymentIntent.CREATE, command);
  }

  public static CommandRecord completeJob(final String variables) {
    final JobRecord command = new JobRecord();
    command.setVariables(transformVariables(variables));

    return new CommandRecord(ValueType.JOB, JobIntent.COMPLETE, command);
  }

  private static DirectBuffer transformVariables(final String variables) {
    return BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(variables));
  }
}
