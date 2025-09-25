/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.common;

import io.camunda.zeebe.engine.common.state.instance.ElementInstance;

public interface ElementInstanceProvider {

  /**
   * Provides an element instance identified by the given elements instance key.
   *
   * @param elementsInstanceKey identifier of the element instance
   * @return the element instance
   */
  ElementInstance getInstance(final long elementsInstanceKey);
}
