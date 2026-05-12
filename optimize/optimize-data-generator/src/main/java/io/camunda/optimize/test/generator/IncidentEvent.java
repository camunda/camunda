/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

/**
 * Groups the identity and timestamp of one incident lifecycle event.
 *
 * <p>Passed to {@link ZeebeRecordFactory#incidentOp} to reduce its argument count.
 *
 * @param incKey the incident key
 * @param elemInstKey the element-instance key of the service task that raised the incident
 * @param elementId the BPMN element ID of the service task
 * @param timestamp epoch-millisecond timestamp of the incident event
 */
record IncidentEvent(long incKey, long elemInstKey, String elementId, long timestamp) {}
