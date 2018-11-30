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
import io.zeebe.util.LangUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import org.agrona.BitUtil;

public class FsSnapshotMetadata implements SnapshotMetadata {
  protected final String name;
  protected final long position;
  protected final long size;
  protected final byte[] checksum;

  public FsSnapshotMetadata(
      final FsSnapshotStorageConfiguration config,
      final File snapshotFile,
      final File checksumFile,
      final long position) {
    this.name = config.getSnapshotNameFromFileName(snapshotFile.getName());
    this.position = position;
    this.size = snapshotFile.length();

    this.checksum = extractAndValidateChecksum(config, snapshotFile, checksumFile);
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
  public byte[] getChecksum() {
    return checksum;
  }

  private byte[] extractAndValidateChecksum(
      final FsSnapshotStorageConfiguration config,
      final File snapshotFile,
      final File checksumFile) {
    final String checksumFileContent = readChecksumContent(checksumFile);

    final byte[] checksum = extractChecksum(config, checksumFileContent);
    final String dataFileName = extractDataFileName(config, checksumFileContent);

    if (!dataFileName.equals(snapshotFile.getName())) {
      throw new RuntimeException("Read invalid snapshot, file name doesn't match.");
    }

    return checksum;
  }

  private String readChecksumContent(final File checksumFile) {
    final String checksumLine;

    try (FileInputStream fileInputStream = new FileInputStream(checksumFile);
        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader); ) {
      checksumLine = bufferedReader.readLine();

      if (checksumLine == null || checksumLine.isEmpty()) {
        throw new RuntimeException("Read invalid checksum file, no content");
      }
    } catch (final IOException ex) {
      LangUtil.rethrowUnchecked(ex);
      return null;
    }

    return checksumLine;
  }

  private byte[] extractChecksum(
      final FsSnapshotStorageConfiguration config, final String content) {
    final String checksumString = config.extractDigestFromChecksumContent(content);
    if (checksumString.isEmpty()) {
      throw new RuntimeException("Read invalid checksum file, missing checksum.");
    }

    return BitUtil.fromHex(checksumString);
  }

  private String extractDataFileName(
      final FsSnapshotStorageConfiguration config, final String content) {
    final String fileName = config.extractDataFileNameFromChecksumContent(content);
    if (fileName.isEmpty()) {
      throw new RuntimeException("Read invalid checksum file, missing data file name.");
    }

    return fileName;
  }
}
