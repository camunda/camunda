/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.model.bpmn.util.time.Timer;

public interface ExecutableCatchEvent extends ExecutableFlowElement {
  boolean isTimer();

  boolean isMessage();

  default boolean isNone() {
    return !isTimer() && !isMessage();
  }

  ExecutableMessage getMessage();

  default boolean shouldCloseMessageSubscriptionOnCorrelate() {
    return true;
  }

  Timer getTimer();
}
