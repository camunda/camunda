/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

/**
 * Thrown when a {@link MailboxStore} cannot complete an operation, for example because the database
 * is not reachable. The operation may or may not have taken effect. See the rules in {@link
 * MailboxStore} for which operations a caller can repeat.
 */
public final class MailboxStoreException extends RuntimeException {

  public MailboxStoreException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
