/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;

public final class ZeebeRuntimeValidators {

  public static final Collection<ModelElementValidator<?>> VALIDATORS =
      List.of(
          ZeebeExpressionValidator.verifyThat(ZeebeInput.class)
              .hasValidPathExpression(ZeebeInput::getSource)
              .hasValidPathExpression(ZeebeInput::getTarget)
              .build(),
          ZeebeExpressionValidator.verifyThat(ZeebeOutput.class)
              .hasValidPathExpression(ZeebeOutput::getSource)
              .hasValidPathExpression(ZeebeOutput::getTarget)
              .build(),
          ZeebeExpressionValidator.verifyThat(ZeebeSubscription.class)
              .hasValidPathExpression(ZeebeSubscription::getCorrelationKey)
              .build(),
          ZeebeExpressionValidator.verifyThat(ZeebeLoopCharacteristics.class)
              .hasValidPathExpression(ZeebeLoopCharacteristics::getInputCollection)
              .hasValidPathExpression(ZeebeLoopCharacteristics::getOutputElement)
              .build(),
          ZeebeExpressionValidator.verifyThat(ZeebeCalledElement.class)
              .hasValidPathExpression(ZeebeCalledElement::getProcessIdExpression)
              .build(),
          new SequenceFlowValidator());
}
