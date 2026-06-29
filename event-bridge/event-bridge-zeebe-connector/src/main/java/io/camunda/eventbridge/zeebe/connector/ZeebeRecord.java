/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import io.camunda.zeebe.protocol.record.Record;

/**
 * A Zeebe record consumed from the Event Bridge, paired with the envelope coordinates that locate
 * it on the log.
 *
 * @param topic the Event Bridge topic the record was read from
 * @param partitionId the Event Bridge partition the record was read from
 * @param offset the Event Bridge log position of the record, used to commit progress
 * @param record the reconstructed Zeebe record
 */
public record ZeebeRecord(String topic, int partitionId, long offset, Record<?> record) {}
