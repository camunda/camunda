/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import io.camunda.zeebe.eventplane.segment.LogId;

/**
 * What the key of a consumer group's cursor says: the group, and the log. See {@link
 * ObjectNames#cursor}.
 *
 * @param group the consumer group
 * @param log the log the cursor is in
 */
public record CursorName(String group, LogId log) {}
