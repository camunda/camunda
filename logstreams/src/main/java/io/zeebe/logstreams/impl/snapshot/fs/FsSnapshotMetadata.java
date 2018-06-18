/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.impl.snapshot.fs;

import io.zeebe.logstreams.spi.SnapshotMetadata;
import java.io.*;
import org.agrona.BitUtil;

public class FsSnapshotMetadata implements SnapshotMetadata {
  private final String name;
  private final long position;
  private final long size;
  private final boolean replicable;
  private final byte[] checksum;

  public FsSnapshotMetadata(
      final String name,
      final long position,
      final long size,
      final boolean replicable,
      final byte[] checksum) {
    this.name = name;
    this.position = position;
    this.size = size;
    this.replicable = replicable;
    this.checksum = checksum;
  }

  public FsSnapshotMetadata(
      final FsSnapshotStorageConfiguration config,
      final File snapshotFile,
      final File checksumFile) {
    this.name = config.getSnapshotNameFromFileName(snapshotFile.getName());
    this.position = config.getPositionOfSnapshotFile(snapshotFile, this.name);
    this.size = snapshotFile.length();
    this.replicable = config.isReplicable(this.name);

    this.checksum = extractChecksum(config, checksumFile);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public long getSize() {
    return size;
  }

  @Override
  public boolean isReplicable() {
    return replicable;
  }

  @Override
  public byte[] getChecksum() {
    return checksum;
  }

  private byte[] extractChecksum(
      final FsSnapshotStorageConfiguration config, final File checksumFile) {
    try (FileInputStream fileInputStream = new FileInputStream(checksumFile);
        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
      final String contents = bufferedReader.readLine();
      final String hexChecksum = config.extractDigestFromChecksumContent(contents);
      return BitUtil.fromHex(hexChecksum);
    } catch (final IOException ex) {
      return null;
    }
  }
}
