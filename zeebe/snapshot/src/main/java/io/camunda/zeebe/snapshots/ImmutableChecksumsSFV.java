/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Immutable checksum collection in simple file verification (SFV) file format, which only allows to
 * read serialized file checksums or a combined checksum.
 */
public interface ImmutableChecksumsSFV {

  /**
   * @return a combined CRC32C checksum over all files (for backwards compatibility)
   */
  long getCombinedValue();

  /**
   * Write the checksum collection in SFV format to the given output stream.
   *
   * @param stream in which the data will be written to
   */
  void write(OutputStream stream) throws IOException;
}
