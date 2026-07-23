/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

/**
 * One buffered {@code open_instances_gauge} observation — a process's open-instance count at a
 * given wall-clock sample tick. See {@link OpenInstancesGaugeSampler}'s class javadoc for why these
 * are observations, not source-log-derived facts.
 *
 * @param sampledAtMs wall-clock epoch millis at the moment this sample's tick was taken
 * @param processId the BPMN process id this count is for
 * @param openInstances how many instances of {@code processId} were open at {@code sampledAtMs};
 *     always greater than zero — {@link OpenInstancesGaugeSampler#tick} never buffers a zero-count
 *     entry (see its own javadoc)
 */
public record GaugeSample(long sampledAtMs, String processId, long openInstances) {}
