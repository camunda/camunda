/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.state.instance.ElementInstance;

/**
 * Suspends one concern of an element instance, such as its user task, while {@link
 * SuspensionBatchProcessor} walks the element instance tree.
 */
interface ElementInstanceSuspensionVisitor {

  /** Suspends what the element instance owns for this concern. */
  void visit(ElementInstance elementInstance);
}
