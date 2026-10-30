/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.log.records;

/** The bytes given to {@link LogRecordReader} aren't a valid entry of a partition's log. */
public final class MalformedLogEntryException extends RuntimeException {

  public MalformedLogEntryException(final String message) {
    super(message);
  }
}
