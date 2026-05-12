/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

/**
 * Groups the identity and timestamp of one user-task lifecycle event.
 *
 * <p>Passed to {@link ZeebeRecordFactory} user-task methods to reduce their argument count. A new
 * instance is created for each lifecycle step (creating, assigned, completed/canceled) because each
 * step has a different timestamp.
 *
 * @param utKey the user-task key
 * @param elemInstKey the element-instance key of the user-task flow node
 * @param elementId the BPMN element ID of the user-task
 * @param timestamp epoch-millisecond timestamp of this lifecycle step
 */
record UserTaskEvent(long utKey, long elemInstKey, String elementId, long timestamp) {}
