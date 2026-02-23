/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.engine.processing.deployment.transform.BpmnValidatorConfig;
import io.camunda.zeebe.model.bpmn.instance.CallableElement;
import io.camunda.zeebe.model.bpmn.instance.Category;
import io.camunda.zeebe.model.bpmn.instance.Collaboration;
import io.camunda.zeebe.model.bpmn.instance.ConversationLink;
import io.camunda.zeebe.model.bpmn.instance.ConversationNode;
import io.camunda.zeebe.model.bpmn.instance.CorrelationKey;
import io.camunda.zeebe.model.bpmn.instance.CorrelationProperty;
import io.camunda.zeebe.model.bpmn.instance.DataInput;
import io.camunda.zeebe.model.bpmn.instance.DataOutput;
import io.camunda.zeebe.model.bpmn.instance.DataState;
import io.camunda.zeebe.model.bpmn.instance.DataStore;
import io.camunda.zeebe.model.bpmn.instance.Definitions;
import io.camunda.zeebe.model.bpmn.instance.Error;
import io.camunda.zeebe.model.bpmn.instance.Escalation;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.InputSet;
import io.camunda.zeebe.model.bpmn.instance.Interface;
import io.camunda.zeebe.model.bpmn.instance.Lane;
import io.camunda.zeebe.model.bpmn.instance.LaneSet;
import io.camunda.zeebe.model.bpmn.instance.LinkEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.MessageFlow;
import io.camunda.zeebe.model.bpmn.instance.Operation;
import io.camunda.zeebe.model.bpmn.instance.OutputSet;
import io.camunda.zeebe.model.bpmn.instance.Participant;
import io.camunda.zeebe.model.bpmn.instance.Property;
import io.camunda.zeebe.model.bpmn.instance.Resource;
import io.camunda.zeebe.model.bpmn.instance.ResourceParameter;
import io.camunda.zeebe.model.bpmn.instance.ResourceRole;
import io.camunda.zeebe.model.bpmn.instance.Signal;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;

public final class ZeebeConfigurationValidators {

  public static Collection<ModelElementValidator<?>> getValidators(
      final BpmnValidatorConfig config) {
    return List.of(
        new IdLengthValidator(config.maxIdFieldLength()),
        new NameLengthValidator<>(CallableElement.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Category.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Collaboration.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(ConversationLink.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(ConversationNode.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(CorrelationKey.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(CorrelationProperty.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(DataInput.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(DataOutput.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(DataState.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(DataStore.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Definitions.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Error.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Escalation.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(InputSet.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Interface.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Lane.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(LaneSet.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(LinkEventDefinition.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Message.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(MessageFlow.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Operation.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(OutputSet.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Participant.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Property.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Resource.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(ResourceParameter.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(ResourceRole.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(Signal.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(ZeebeProperty.class, config.maxNameFieldLength()),
        new NameLengthValidator<>(FlowElement.class, config.maxNameFieldLength()),
        new WorkerTypeLengthValidator(config.maxWorkerTypeLength()));
  }
}
