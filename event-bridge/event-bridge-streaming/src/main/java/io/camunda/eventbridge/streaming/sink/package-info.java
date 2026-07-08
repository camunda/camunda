/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Sink-side building blocks for a task's frozen commit cut (streaming ADR 0005): staging produced
 * output on the owner thread so the IO thread can drain it to its destination between freeze and
 * completion. Tasks wrap these with their domain logic (encoding, transport, idempotent replay).
 */
package io.camunda.eventbridge.streaming.sink;
