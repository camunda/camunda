/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import java.io.IOException;
import java.io.OutputStream;
import java.util.SortedMap;

/**
 * An immutable description of all files in a snapshot, including their per-file checksums and a
 * combined integrity checksum used for snapshot verification.
 *
 * <p>The on-disk representation uses the Simple File Verification (SFV) format, which records only
 * checksums. The "manifest" naming was chosen over "checksum" because the scope of this type is
 * expanding to also carry file-size metadata for replication-lag accounting — the SFV file on disk
 * remains purely a checksum artifact.
 */
public interface SnapshotManifest {

  /**
   * Write the checksum collection in SFV format to the given output stream.
   *
   * @param stream in which the data will be written to
   */
  void write(OutputStream stream) throws IOException;

  /**
   * @return the map containing the individual file checksums
   */
  SortedMap<String, Long> getChecksums();

  /**
   * Returns if all file checksums match exactly.
   *
   * @param o The other checksum
   * @return boolean denoting match
   */
  boolean sameChecksums(SnapshotManifest o);

  /**
   * Returns some combined checksum over all the file checksums. If any of the individual checksums
   * or file names change, the combined checksum will change as well.
   */
  long getCombinedChecksum();
}
