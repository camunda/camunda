/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.snapshots.SnapshotFileInfoProvider;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import org.rocksdb.LiveFileMetaData;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public class RocksDBSnapshotFileInfoProvider implements SnapshotFileInfoProvider {

  @Override
  public SnapshotFilesInfo getSnapshotFilesInfo(final Path snapshotPath) {
    try (final var db = RocksDB.openReadOnly(snapshotPath.toString())) {
      final var checksums = new HashMap<String, Long>();
      final var sizes = new HashMap<String, Long>();

      for (final var fileMetaData : db.getLiveFilesMetaData()) {
        final var fileName = getMetadataName(fileMetaData);
        if (fileMetaData.fileChecksum().length != 0) {
          checksums.put(fileName, rocksDBChecksumAsLong(fileMetaData));
        }
        sizes.put(fileName, fileMetaData.size());
      }

      return new SnapshotFilesInfo(
          Collections.unmodifiableMap(checksums), Collections.unmodifiableMap(sizes));
    } catch (final RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  private String getMetadataName(final LiveFileMetaData fileMetaData) {
    // RocksDB prefixes live file names with '/'; strip it to match Path.getFileName.
    final var fileName = fileMetaData.fileName();
    return fileName.startsWith("/") ? fileName.substring(1) : fileName;
  }

  private Long rocksDBChecksumAsLong(final LiveFileMetaData fileMetaData) {
    final var checksum = fileMetaData.fileChecksum();
    return Integer.toUnsignedLong(ByteBuffer.wrap(checksum).order(ByteOrder.BIG_ENDIAN).getInt());
  }
}
