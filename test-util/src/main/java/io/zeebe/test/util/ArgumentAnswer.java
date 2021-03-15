/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Identity function on one of the arguments
 *
 * @author Lindhauer
 */
public final class ArgumentAnswer<T> implements Answer<T> {

  protected final int argIndex;

  public ArgumentAnswer(final int argIndex) {
    this.argIndex = argIndex;
  }

  @Override
  public T answer(final InvocationOnMock invocation) throws Throwable {
    return (T) invocation.getArguments()[argIndex];
  }
}
