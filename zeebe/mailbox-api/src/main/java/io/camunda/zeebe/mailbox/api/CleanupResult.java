/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

/**
 * The number of rows that {@link MailboxStore#deleteExpired(java.time.Duration,
 * java.time.Duration)} removed.
 *
 * @param inboxDeleted the number of inbox messages removed
 * @param outboxDeleted the number of outbox responses removed
 */
public record CleanupResult(int inboxDeleted, int outboxDeleted) {}
