/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The properties of a process that is marked as a case via {@code zeebe:case}. The presence of
 * these properties on a process is what marks it as a case.
 */
@NullMarked
public class CaseProperties {

  private @Nullable DirectBuffer titleVariable;

  /**
   * @return the name of the variable that holds the case title, or {@code null} if no usable name
   *     is configured
   */
  public @Nullable DirectBuffer getTitleVariable() {
    return titleVariable;
  }

  public void setTitleVariable(final @Nullable DirectBuffer titleVariable) {
    this.titleVariable = titleVariable;
  }
}
