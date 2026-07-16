/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

/**
 * Immutable metadata describing one clean segment file, as recorded in the {@link
 * CompactionManifest}. The manifest holds the truth about which segments make up the clean set and
 * in what order; the file on disk is self-framing (a sequence of single-entry batches) and carries
 * no index of its own (ADR 0001, decision 5).
 *
 * <p>Every field is derived from the log content, never from a replica's wall clock — this is part
 * of what keeps manifests byte-identical across replicas. {@code byteLength} and {@code crc32} let
 * the manifest loader detect a missing or torn segment loudly: a segment whose file is absent, the
 * wrong length, or fails its checksum makes the whole manifest fail to load rather than silently
 * serving corrupt history.
 *
 * @param fileName the segment's file name, relative to the compaction directory
 * @param firstPosition the log position of the first (lowest-position) record in the segment; used
 *     for O(log n) segment selection during fetch
 * @param byteLength the exact byte length of the segment file
 * @param crc32 the CRC-32 of the entire segment file's bytes
 */
public record CleanSegment(String fileName, long firstPosition, long byteLength, long crc32) {}
