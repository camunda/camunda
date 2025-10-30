/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

public interface MultiInstanceState {

  /**
   * Retrieves the evaluated input collection for a multi-instance body.
   *
   * <p>Storing the input collection was introduced in later versions. It could be that existing
   * multi-instance bodies do not have an input collection stored in the state. Because of this the
   * method returns an optional.
   *
   * @param multiInstanceKey The key of the multi-instance element instance.
   * @return A optional list of buffers representing the input collection.
   */
  Optional<List<DirectBuffer>> getInputCollection(long multiInstanceKey);
}
