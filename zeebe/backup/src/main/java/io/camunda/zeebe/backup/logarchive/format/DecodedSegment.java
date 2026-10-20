/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import java.util.List;

/**
 * A decoded segment.
 *
 * <p>The frames hold what the journal stores: index, asqn, checksum and the serialized {@code
 * RecordData}. The Raft entry inside is not interpreted (see {@link RaftEntryInfo}), so {@code
 * term} and {@code highestPosition} of the frames are -1 and {@code lowestPosition} is the asqn;
 * positions of the whole segment are in the footer.
 *
 * @param footer the footer
 * @param identity who wrote the segment
 * @param frameFirstPositions per frame of the body, its first position or -1
 * @param frameOffsets per frame of the body, its offset in the object
 * @param frames all entries in index order
 */
public record DecodedSegment(
    SegmentFooter footer,
    SegmentIdentity identity,
    List<Long> frameFirstPositions,
    List<Long> frameOffsets,
    List<LogArchiveFrame> frames) {}
