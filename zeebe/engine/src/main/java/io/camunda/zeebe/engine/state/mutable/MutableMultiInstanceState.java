/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MultiInstanceState;
import java.util.List;
import org.agrona.DirectBuffer;

public interface MutableMultiInstanceState extends MultiInstanceState {

  /**
   * Inserts the evaluated input collection for a multi-instance body into the state.
   *
   * @param multiInstanceKey The key of the multi-instance element instance.
   * @param inputCollection The evaluated input collection as a list of buffers.
   */
  void insertInputCollection(long multiInstanceKey, List<DirectBuffer> inputCollection);

  /**
   * Deletes the input collection for a multi-instance body from the state.
   *
   * @param multiInstanceKey The key of the multi-instance element instance.
   */
  void deleteInputCollection(long multiInstanceKey);
}
