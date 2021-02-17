/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random;

import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.model.bpmn.builder.StartEventBuilder;
import java.util.Map;

/**
 * Implementations of this class build the start block of processes and the initial execution paths.
 *
 * <p>Implementations of this class must adhere to the following contract:
 *
 * <ul>
 *   <li>Any randomness in terms of the generated structure must happen at construction time
 *   <li>The method {@code buildStartEvent(...)} must be deterministic
 *   <li>The method {@code findRandomExecutionPath(...)} must be deterministic for the same random
 *       noise generator
 *   <li>The methods {@code buildStartEvent(...)} and {@code findRandomExecutionPath(...} call any
 *       relevant nested block builders recursively
 * </ul>
 */
public interface StartEventBlockBuilder {

  /**
   * Appends the start event to the given process.
   *
   * <p><strong>Contract: </strong> this method must be deterministic. It must not have any random
   * behavior.
   */
  StartEventBuilder buildStartEvent(final ProcessBuilder processBuilder);

  /** Creates a random execution path segment. */
  ExecutionPathSegment findRandomExecutionPath(String processId, Map<String, Object> variables);
}
