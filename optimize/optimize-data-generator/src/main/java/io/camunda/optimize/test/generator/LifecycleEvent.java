/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

/**
 * Groups the intent and timestamp of one process-instance lifecycle transition.
 *
 * <p>Paired with {@link ElementRecord} to reduce {@link ZeebeRecordFactory#processInstanceOp} from
 * 7 arguments to 3.
 *
 * @param intent the lifecycle intent (e.g. {@code ELEMENT_ACTIVATING}, {@code ELEMENT_COMPLETED})
 * @param timestamp epoch-millisecond timestamp of the transition
 */
record LifecycleEvent(ProcessInstanceIntent intent, long timestamp) {}
