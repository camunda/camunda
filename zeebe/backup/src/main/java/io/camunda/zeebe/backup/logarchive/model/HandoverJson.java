/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The handover from continuous backup to the log archive (spec section 18.2): the checkpoint that
 * divides the history of the partition. Carried by the {@code HANDOVER} commit entry and by every
 * root written after it.
 *
 * @param checkpointId the id of the handover checkpoint H
 * @param checkpointPosition the log position of H
 */
@JsonPropertyOrder({"checkpointId", "checkpointPosition"})
public record HandoverJson(long checkpointId, long checkpointPosition) {}
