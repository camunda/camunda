/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.transformation;

import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.zeebe.model.bpmn.traversal.TypeHierarchyVisitor;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.model.xml.type.ModelElementType;

public final class TransformationVisitor extends TypeHierarchyVisitor {

  private final Map<Class<?>, ModelElementTransformer<?>> transformHandlers = new HashMap<>();

  private TransformContext context;

  public TransformContext getContext() {
    return context;
  }

  public void setContext(final TransformContext context) {
    this.context = context;
  }

  public void registerHandler(final ModelElementTransformer<?> transformHandler) {
    transformHandlers.put(transformHandler.getType(), transformHandler);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  protected void visit(
      final ModelElementType implementedType, final BpmnModelElementInstance instance) {
    final ModelElementTransformer handler =
        transformHandlers.get(implementedType.getInstanceType());

    if (handler != null) {
      handler.transform(instance, context);
    }
  }
}
