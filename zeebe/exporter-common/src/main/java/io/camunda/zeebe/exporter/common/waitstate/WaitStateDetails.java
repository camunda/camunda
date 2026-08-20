/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

/**
 * Marker interface for wait-state-specific detail objects.
 *
 * <p>Each concrete implementation carries the fields relevant to one wait-state type (e.g. {@code
 * JobWaitStateDetails} for job-based waits). Backend exporters are responsible for serialising the
 * details into their respective storage format.
 */
public interface WaitStateDetails {}
