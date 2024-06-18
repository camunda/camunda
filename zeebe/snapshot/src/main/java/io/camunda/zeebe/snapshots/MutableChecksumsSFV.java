/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import java.io.IOException;
import java.nio.file.Path;

/** Mutable checksum collection in simple file verification (SFV) file format */
public interface MutableChecksumsSFV extends ImmutableChecksumsSFV {

  /**
   * Update the checksum collection, and add a new checksum from a given file path.
   *
   * @param filePath the path to a file for which a checksum is created and added to the collection
   * @throws IOException when reading of given file fails
   */
  void updateFromFile(final Path filePath) throws IOException;

  /**
   * Build the checksum collection from a SFV format string array.
   *
   * @param lines the lines (in SFV) to build up the checksum collection
   */
  void updateFromSfvFile(final String... lines);

  /**
   * Update the checksum map with the given checksum
   *
   * @param filePath path to file
   * @param checksum check of file given
   */
  void updateFromChecksum(final Path filePath, final long checksum);

  /**
   * Update the checksum map with the contents of the given snapshot chunk
   *
   * @param snapshotChunk
   */
  void updateFromSnapshotChunk(final SnapshotChunk snapshotChunk);

  /**
   * Returns if all file checksums match exactly.
   *
   * @param o The other checksum
   * @return boolean denoting match
   */
  boolean sameChecksums(ImmutableChecksumsSFV o);
}
