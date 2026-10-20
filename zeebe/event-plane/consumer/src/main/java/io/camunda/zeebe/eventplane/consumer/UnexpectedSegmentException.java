/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

/**
 * A segment whose content contradicts its key: another log, history or indexes in its footer and
 * metadata than its name says, or fewer bytes than the listing said.
 */
public final class UnexpectedSegmentException extends RuntimeException {

  private final String key;

  UnexpectedSegmentException(final String key, final String expectation) {
    super(expectation + ", in segment '" + key + "'");
    this.key = key;
  }

  /** The segment's key. */
  public String key() {
    return key;
  }
}
