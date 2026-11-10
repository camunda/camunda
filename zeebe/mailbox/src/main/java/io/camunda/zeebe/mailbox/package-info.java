/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Logic of the cross-region mailbox: the routing decision, the loops that move messages out of an
 * inbox and responses out of an outbox, and an in-memory store for tests.
 *
 * <p>The classes do not start threads or actors. Each loop has a method that does one step, and the
 * caller decides when to call it. This keeps the logic testable without a clock.
 */
@NullMarked
package io.camunda.zeebe.mailbox;

import org.jspecify.annotations.NullMarked;
