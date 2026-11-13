/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

/**
 * The {@code int} field of a {@link MsgpackPojo} that has no default, like a {@code
 * IntegerProperty} declared without one: it is either unset or holds a value. Writing a POJO with
 * an unset holder fails, and so does reading a message without its key. The field must be {@code
 * final}: the holder is read into and reset in place, so reading does not allocate.
 */
public final class RequiredInt {

  private int value;
  private boolean isSet;

  public void set(final int value) {
    this.value = value;
    isSet = true;
  }

  /**
   * @throws IllegalStateException if no value is set
   */
  public int get() {
    if (!isSet) {
      throw new IllegalStateException(
          "Expected a value or default value to be specified, but has nothing");
    }
    return value;
  }

  public boolean isSet() {
    return isSet;
  }

  public void reset() {
    value = 0;
    isSet = false;
  }
}
