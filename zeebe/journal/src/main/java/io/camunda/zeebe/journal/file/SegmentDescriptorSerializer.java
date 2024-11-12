/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import java.nio.ByteBuffer;
import java.util.Arrays;

interface SegmentDescriptorSerializer {

  byte CUR_VERSION = 2;
  byte[] SUPPORTED_VERSIONS = new byte[] {CUR_VERSION};

  static short currentEncodingLength() {
    return SegmentDescriptorSerializerSbe.ENCODING_LENGTH;
  }

  /**
   * Major version descriptor:
   *
   * <ol>
   *   <li>not in used anymore
   *   <li>SBE (current) current descriptor
   * </ol>
   *
   * @return
   */
  byte majorVersion();

  /**
   * @return Minor version descriptor, representing forward & backward compatible changes to the
   *     schema
   */
  byte minorVersion();

  /**
   * The number of bytes required to write a descriptor to the segment. It's a fixed number for each
   * serializer.
   *
   * @return the encoding length
   */
  int encodingLength();

  /**
   * Writes the segmentDescriptor into the buffer, if the actingSchemaVersion of the descriptor is
   * equal or newer than that of this serializer. In case an old record is passed, the serialization
   * will be skipped altogether.
   *
   * @param segmentDescriptor the descriptor to write
   * @param buffer the buffer to writeTo
   */
  void writeTo(SegmentDescriptor segmentDescriptor, ByteBuffer buffer);

  /**
   * try to read a SegmentDescriptor from a buffer.
   *
   * @param buffer to read from
   * @return the segment descriptor
   * @throws UnknownVersionException if the version is not compatible with this serializer
   * @throws io.camunda.zeebe.journal.CorruptedJournalException if the checksum check fails
   */
  SegmentDescriptor readFrom(ByteBuffer buffer);

  static SegmentDescriptorSerializer currentSerializer() {
    return forVersion(CUR_VERSION);
  }

  static SegmentDescriptorSerializer forVersion(final byte version) {
    return switch (version) {
      case CUR_VERSION -> new SegmentDescriptorSerializerSbe();
      default ->
          throw new IllegalArgumentException(
              "Version %d is not supported. Supported versions are %s"
                  .formatted(version, Arrays.toString(SUPPORTED_VERSIONS)));
    };
  }
}
