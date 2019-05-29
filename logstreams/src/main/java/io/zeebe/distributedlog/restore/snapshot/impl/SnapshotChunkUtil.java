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
package io.zeebe.distributedlog.restore.snapshot.impl;

import io.zeebe.logstreams.state.SnapshotChunk;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.CRC32;

public class SnapshotChunkUtil {

  public static long createChecksum(byte[] content) {
    final CRC32 crc32 = new CRC32();
    crc32.update(content);
    return crc32.getValue();
  }

  public static SnapshotChunk createSnapshotChunkFromFile(
      File snapshotChunkFile, long snapshotPosition, int totalCount) throws IOException {
    final byte[] content;
    content = Files.readAllBytes(snapshotChunkFile.toPath());
    final long checksum = createChecksum(content);
    return new SnapshotChunkImpl(
        snapshotPosition, totalCount, snapshotChunkFile.getName(), checksum, content);
  }

  private static final class SnapshotChunkImpl implements SnapshotChunk {
    private final long snapshotPosition;
    private final int totalCount;
    private final String chunkName;
    private final byte[] content;
    private final long checksum;

    SnapshotChunkImpl(
        long snapshotPosition, int totalCount, String chunkName, long checksum, byte[] content) {
      this.snapshotPosition = snapshotPosition;
      this.totalCount = totalCount;
      this.chunkName = chunkName;
      this.checksum = checksum;
      this.content = content;
    }

    @Override
    public long getSnapshotPosition() {
      return snapshotPosition;
    }

    @Override
    public String getChunkName() {
      return chunkName;
    }

    @Override
    public int getTotalCount() {
      return totalCount;
    }

    @Override
    public long getChecksum() {
      return checksum;
    }

    @Override
    public byte[] getContent() {
      return content;
    }
  }
}
