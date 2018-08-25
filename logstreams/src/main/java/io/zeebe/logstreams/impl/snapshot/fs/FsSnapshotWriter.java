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

import static io.zeebe.util.StringUtil.getBytes;

import io.zeebe.logstreams.spi.SnapshotWriter;
import io.zeebe.util.FileUtil;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import org.agrona.BitUtil;
import org.agrona.LangUtil;
import org.slf4j.Logger;

public class FsSnapshotWriter implements SnapshotWriter {
  public static final Logger LOG = io.zeebe.logstreams.impl.Loggers.LOGSTREAMS_LOGGER;
  protected final FsSnapshotStorageConfiguration config;
  protected final File dataFile;
  protected final File checksumFile;
  protected final FsReadableSnapshot lastSnapshot;

  protected DigestOutputStream dataOutputStream;

  public FsSnapshotWriter(
      FsSnapshotStorageConfiguration config,
      File snapshotFile,
      File checksumFile,
      FsReadableSnapshot lastSnapshot) {
    this.config = config;
    this.dataFile = snapshotFile;
    this.checksumFile = checksumFile;
    this.lastSnapshot = lastSnapshot;

    initOutputStreams(config, snapshotFile);
  }

  protected void initOutputStreams(FsSnapshotStorageConfiguration config, File snapshotFile) {
    try {
      final MessageDigest messageDigest = MessageDigest.getInstance(config.getChecksumAlgorithm());

      final FileOutputStream dataFileOutputStream = new FileOutputStream(snapshotFile);
      final BufferedOutputStream bufferedDataOutputStream =
          new BufferedOutputStream(dataFileOutputStream);
      dataOutputStream = new DigestOutputStream(bufferedDataOutputStream, messageDigest);
    } catch (Exception e) {
      abort();
      LangUtil.rethrowUnchecked(e);
    }
  }

  @Override
  public OutputStream getOutputStream() {
    return dataOutputStream;
  }

  @Override
  public void commit() throws Exception {
    commit(closeAndGetChecksum());
  }

  @Override
  public void validateAndCommit(final byte[] checksum) throws Exception {
    final byte[] writtenChecksum = closeAndGetChecksum();
    if (Arrays.equals(writtenChecksum, checksum)) {
      commit(checksum);
    } else {
      abort();
      throw new RuntimeException(
          String.format(
              "Mismatched checksums, expected %s, got %s",
              BitUtil.toHex(checksum), BitUtil.toHex(writtenChecksum)));
    }
  }

  @Override
  public void abort() {
    FileUtil.closeSilently(dataOutputStream);
    FileUtil.deleteFile(dataFile);
    FileUtil.deleteFile(checksumFile);
  }

  public File getChecksumFile() {
    return checksumFile;
  }

  public File getDataFile() {
    return dataFile;
  }

  protected String getDataFileName() {
    return dataFile.getName();
  }

  private byte[] closeAndGetChecksum() throws Exception {
    final byte[] checksum;

    try {
      dataOutputStream.close();
      final MessageDigest digest = dataOutputStream.getMessageDigest();
      checksum = digest.digest();
    } catch (final Exception ex) {
      abort();
      throw ex;
    }

    return checksum;
  }

  protected void commit(final byte[] checksum) throws Exception {
    try {
      writeChecksumFile(checksum);
    } catch (final Exception ex) {
      abort();
      throw ex;
    }

    deleteLastSnapshot();
  }

  protected void writeChecksumFile(final byte[] checksum) throws Exception {
    Files.createFile(checksumFile.toPath());

    try (FileOutputStream checksumFileOutputStream = new FileOutputStream(checksumFile);
        BufferedOutputStream checksumOutputStream =
            new BufferedOutputStream(checksumFileOutputStream)) {
      final String checksumHex = BitUtil.toHex(checksum);
      final String checksumFileContents = config.checksumContent(checksumHex, getDataFileName());

      checksumOutputStream.write(getBytes(checksumFileContents));
    }
  }

  protected void deleteLastSnapshot() {
    if (lastSnapshot != null) {
      LOG.info("Delete last snapshot file {}.", lastSnapshot.getDataFile());
      lastSnapshot.delete();
    }
  }
}
