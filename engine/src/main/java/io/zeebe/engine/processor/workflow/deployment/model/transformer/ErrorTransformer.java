/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableError;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Error;

public class ErrorTransformer implements ModelElementTransformer<Error> {

  @Override
  public Class<Error> getType() {
    return Error.class;
  }

  @Override
  public void transform(final Error element, final TransformContext context) {

    final var error = new ExecutableError(element.getId());
    error.setErrorCode(wrapString(element.getErrorCode()));

    context.addError(error);
  }
}
