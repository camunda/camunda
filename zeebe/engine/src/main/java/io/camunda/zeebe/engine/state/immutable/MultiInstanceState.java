/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import java.util.List;
import org.agrona.DirectBuffer;

public interface MultiInstanceState {

  /**
   * Retrieves the evaluated input collection for a multi-instance body.
   *
   * @param multiInstanceKey The key of the multi-instance element instance.
   * @return A list of buffers representing the input collection.
   */
  List<DirectBuffer> getInputCollection(long multiInstanceKey);
}
