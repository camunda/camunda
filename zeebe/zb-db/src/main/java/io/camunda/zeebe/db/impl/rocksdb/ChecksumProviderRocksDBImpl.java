/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.snapshots.CRC32CChecksumProvider;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.rocksdb.LiveFileMetaData;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public class ChecksumProviderRocksDBImpl implements CRC32CChecksumProvider {

  @Override
  public Map<String, Long> getSnapshotChecksums(final Path snapshotPath) {
    try (final var db = RocksDB.openReadOnly(snapshotPath.toString())) {
      return db.getLiveFilesMetaData().stream()
          .filter(fileMetaData -> fileMetaData.fileChecksum().length != 0)
          .collect(Collectors.toMap(this::getMetadataName, this::rocksDBChecksumAsLong));
    } catch (final RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  private String getMetadataName(final LiveFileMetaData fileMetaData) {
    return fileMetaData.fileName().substring(1);
    //        there is a leading '/' which breaks interactions with the Java Path.getFileName which
    //     returns the file name without a leading '/'
  }

  private Long rocksDBChecksumAsLong(final LiveFileMetaData fileMetaData) {
    final var checksum = fileMetaData.fileChecksum();
    return Integer.toUnsignedLong(ByteBuffer.wrap(checksum).order(ByteOrder.BIG_ENDIAN).getInt());
  }
}
