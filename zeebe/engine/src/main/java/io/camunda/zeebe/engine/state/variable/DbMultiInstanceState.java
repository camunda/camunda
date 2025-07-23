/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.engine.state.mutable.MutableMultiInstanceState;
import java.util.List;
import org.agrona.DirectBuffer;

public class DbMultiInstanceState implements MutableMultiInstanceState {

  @Override
  public void insertInputCollection(
      final long multiInstanceKey, final List<DirectBuffer> inputCollection) {}

  @Override
  public void deleteInputCollection(final long multiInstanceKey) {}

  @Override
  public List<DirectBuffer> getInputCollection(final long multiInstanceKey) {
    return List.of();
  }
}
