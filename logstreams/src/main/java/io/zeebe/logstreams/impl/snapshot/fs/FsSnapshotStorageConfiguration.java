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

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FsSnapshotStorageConfiguration {
  protected static final String CHECKSUM_ALGORITHM = "SHA1";

  protected static final String SNAPSHOT_FILE_NAME_TEMPLATE = "%s-%d.snapshot";
  protected static final String SNAPSHOT_FILE_PATH_TEMPLATE =
      "%s" + File.separatorChar + SNAPSHOT_FILE_NAME_TEMPLATE;
  protected static final String SNAPSHOT_FILE_NAME_PATTERN = "%s-(\\d+)\\.snapshot";

  protected static final String CHECKSUM_FILE_NAME_TEMPLATE =
      "%s" + File.separatorChar + "%s-%d." + CHECKSUM_ALGORITHM.toLowerCase();

  protected static final String CHECKSUM_CONTENT_SEPARATOR = "  ";
  protected static final String CHECKSUM_CONTENT_TEMPLATE =
      "%s" + CHECKSUM_CONTENT_SEPARATOR + "%s" + System.lineSeparator();

  protected String rootPath;

  public void setRootPath(String rootPath) {
    this.rootPath = rootPath;
  }

  public String getRootPath() {
    return rootPath;
  }

  public String getChecksumAlgorithm() {
    return CHECKSUM_ALGORITHM;
  }

  public String snapshotFileName(String name, long logPosition) {
    return String.format(SNAPSHOT_FILE_PATH_TEMPLATE, rootPath, name, logPosition);
  }

  public String checksumFileName(String name, long logPosition) {
    return String.format(CHECKSUM_FILE_NAME_TEMPLATE, rootPath, name, logPosition);
  }

  public boolean matchesSnapshotFileNamePattern(File file, String name) {
    final String pattern = String.format(SNAPSHOT_FILE_NAME_PATTERN, name);
    return file.getName().matches(pattern);
  }

  public boolean isSnapshotFile(final File file) {
    return matchesSnapshotFileNamePattern(file, ".+");
  }

  public Long getPositionOfSnapshotFile(File file, String name) {
    final String fileName = file.getName();

    final String pattern = String.format(SNAPSHOT_FILE_NAME_PATTERN, name);
    final Matcher matcher = Pattern.compile(pattern).matcher(fileName);
    if (matcher.find()) {
      final String position = matcher.group(1);
      return Long.parseLong(position);
    } else {
      throw new IllegalArgumentException("Cannot resolve position of snapshot file: " + fileName);
    }
  }

  public String checksumContent(String checksum, String dataFileName) {
    return String.format(CHECKSUM_CONTENT_TEMPLATE, checksum, dataFileName);
  }

  public String extractDigestFromChecksumContent(String content) {
    final int indexOfSeparator = content.indexOf(CHECKSUM_CONTENT_SEPARATOR);
    if (indexOfSeparator < 0) {
      throw new RuntimeException("Read invalid checksum file, missing separator.");
    }

    return content.substring(0, indexOfSeparator);
  }

  public String extractDataFileNameFromChecksumContent(String content) {
    final int indexOfSeparator = content.indexOf(CHECKSUM_CONTENT_SEPARATOR);
    if (indexOfSeparator < 0) {
      throw new RuntimeException("Read invalid checksum file, missing separator.");
    }

    return content.substring(indexOfSeparator + CHECKSUM_CONTENT_SEPARATOR.length());
  }

  public String getSnapshotNameFromFileName(final String fileName) {
    final String suffixPattern = String.format(SNAPSHOT_FILE_NAME_PATTERN, "");
    final Pattern pattern = Pattern.compile(suffixPattern);
    final String[] parts = pattern.split(fileName);

    return parts[0];
  }
}
