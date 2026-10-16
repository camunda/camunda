/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;

/**
 * Asks the publisher to seal an index chunk: PUT {@code chunkJson} to {@code chunk.key}, then write
 * {@code SEAL_INDEX} carrying exactly the {@code chunk} fields, including the sha256 of {@code
 * chunkJson}.
 */
public record SealRequest(IndexChunk chunk, byte[] chunkJson) {}
