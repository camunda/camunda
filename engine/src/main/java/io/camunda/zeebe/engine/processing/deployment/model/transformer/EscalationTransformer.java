/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEscalation;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Escalation;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;

public class EscalationTransformer implements ModelElementTransformer<Escalation> {

  @Override
  public Class<Escalation> getType() {
    return Escalation.class;
  }

  @Override
  public void transform(final Escalation element, final TransformContext context) {
    final var escalation = new ExecutableEscalation(element.getId());
    Optional.ofNullable(element.getEscalationCode())
        .map(BufferUtil::wrapString)
        .ifPresent(escalation::setEscalationCode);
    context.addEscalation(escalation);
  }
}
