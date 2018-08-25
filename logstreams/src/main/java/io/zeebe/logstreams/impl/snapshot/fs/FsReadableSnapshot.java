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

import io.zeebe.logstreams.snapshot.InvalidSnapshotException;
import io.zeebe.logstreams.spi.ReadableSnapshot;
import io.zeebe.util.FileUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import org.agrona.LangUtil;

public class FsReadableSnapshot extends FsSnapshotMetadata implements ReadableSnapshot {
  protected final FsSnapshotStorageConfiguration config;

  protected final File dataFile;
  protected final File checksumFile;
  protected DigestInputStream inputStream;

  public FsReadableSnapshot(
      FsSnapshotStorageConfiguration config, File dataFile, File checksumFile, long position) {
    super(config, dataFile, checksumFile, position);

    this.config = config;
    this.dataFile = dataFile;
    this.checksumFile = checksumFile;

    tryInit();
  }

  protected void tryInit() {
    try {
      this.inputStream = initDataInputStream();
    } catch (Exception e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  protected DigestInputStream initDataInputStream() throws Exception {
    final MessageDigest messageDigest = MessageDigest.getInstance(config.getChecksumAlgorithm());

    final FileInputStream fileInputStream = new FileInputStream(dataFile);
    final BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);

    return new DigestInputStream(bufferedInputStream, messageDigest);
  }

  @Override
  public void validateAndClose() throws InvalidSnapshotException {
    final MessageDigest messageDigest = inputStream.getMessageDigest();

    FileUtil.closeSilently(inputStream);

    final byte[] digestOfBytesRead = messageDigest.digest();
    final boolean digestsEqual = Arrays.equals(digestOfBytesRead, checksum);

    if (!digestsEqual) {
      throw new InvalidSnapshotException("Read invalid snapshot, checksum doesn't match.");
    }
  }

  @Override
  public void delete() {
    FileUtil.closeSilently(inputStream);

    FileUtil.deleteFile(dataFile);
    FileUtil.deleteFile(checksumFile);
  }

  @Override
  public InputStream getData() {
    return inputStream;
  }

  public File getChecksumFile() {
    return checksumFile;
  }

  public File getDataFile() {
    return dataFile;
  }
}
