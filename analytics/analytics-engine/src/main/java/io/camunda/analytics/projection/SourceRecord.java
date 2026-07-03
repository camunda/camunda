/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.zeebe.protocol.record.Record;

/**
 * A decoded Zeebe {@link Record} paired with the source coordinate it was consumed from — the
 * partition and log offset. The base projection stamps derived facts with this coordinate so the
 * aggregation stage can deduplicate replays.
 *
 * <p>This keeps analytics-engine dependent only on {@code zeebe-protocol} for the record shape: the
 * application decodes the transport payload into a zeebe-protocol {@link Record} and wraps it here,
 * so the projection has no dependency on the Event Bridge connector or its wire format.
 *
 * @param partitionId the source partition the record was read from
 * @param offset the source log offset, used as the derived fact's origin coordinate
 * @param record the decoded Zeebe record
 */
public record SourceRecord(int partitionId, long offset, Record<?> record) {}
