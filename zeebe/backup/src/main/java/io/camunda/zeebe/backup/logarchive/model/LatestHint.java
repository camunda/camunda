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
 * The content of {@code _latest.json} (spec section 9.4). It is a hint: readers that find it
 * missing list {@code root/} instead. It is the only object that is ever overwritten with different
 * content.
 *
 * @param rootSeq the seq of the newest published root
 */
@JsonPropertyOrder({"formatVersion", "timelineId", "partitionId", "rootSeq"})
public record LatestHint(int formatVersion, String timelineId, int partitionId, long rootSeq) {}
