/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.dispatcher;

import org.agrona.DirectBuffer;

/**
 * Consume fragments from the buffer.
 *
 * <p>Note that the handler is not aware of fragment batches.
 */
@FunctionalInterface
public interface FragmentHandler {
  /** fragment consumed successfully * */
  int CONSUME_FRAGMENT_RESULT = 0;

  /** fragment not consumed * */
  int POSTPONE_FRAGMENT_RESULT = 1;

  /** fragment consumed with failure * */
  int FAILED_FRAGMENT_RESULT = 2;

  /**
   * Consume the message from the given buffer.
   *
   * @param buffer the buffer which contains the message
   * @param offset the message offset (i.e. the start position of the message in the buffer)
   * @param length the message length
   * @param streamId the fragments stream id
   * @param isMarkedFailed <code>true</code> if a handler consumed this message previously with
   *     failure (i.e. result was {@link #FAILED_FRAGMENT_RESULT}).
   * @return the consume result which should be one of {@link #CONSUME_FRAGMENT_RESULT}, {@link
   *     #FAILED_FRAGMENT_RESULT} or {@link #POSTPONE_FRAGMENT_RESULT}.
   */
  int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed);
}
