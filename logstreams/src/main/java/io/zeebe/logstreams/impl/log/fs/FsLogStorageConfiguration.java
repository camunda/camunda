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
package io.zeebe.logstreams.impl.log.fs;

import java.io.File;

public class FsLogStorageConfiguration {
  protected static final String FRAGMENT_FILE_NAME_TEMPLATE =
      "%s" + File.separatorChar + "%02d.data";
  protected static final String FRAGMENT_FILE_NAME_PATTERN = "\\d+.data";

  protected static final String SEGMENT_FILE_TRUNCATED_SUFFIX = ".truncated";
  protected static final String SEGMENT_FILE_BACKUP_SUFFIX = ".bak";

  protected static final String BACKUP_FILE_NAME_PATTERN =
      FRAGMENT_FILE_NAME_PATTERN + SEGMENT_FILE_BACKUP_SUFFIX;
  protected static final String BACKUP_FILE_NAME_TEMPLATE =
      FRAGMENT_FILE_NAME_TEMPLATE + SEGMENT_FILE_BACKUP_SUFFIX;

  protected static final String TRUNCATED_FILE_NAME_PATTERN =
      BACKUP_FILE_NAME_PATTERN + SEGMENT_FILE_TRUNCATED_SUFFIX;
  protected static final String TRUNCATED_FILE_NAME_TEMPLATE =
      BACKUP_FILE_NAME_TEMPLATE + SEGMENT_FILE_TRUNCATED_SUFFIX;

  protected final int segmentSize;
  protected final String path;
  protected final int initialSegmentId;
  protected final boolean deleteOnClose;

  public FsLogStorageConfiguration(
      int segmentSize, String path, int initialSegmentId, boolean deleteOnClose) {
    this.segmentSize = segmentSize;
    this.path = path;
    this.initialSegmentId = initialSegmentId;
    this.deleteOnClose = deleteOnClose;
  }

  public String getFragmentFileNameTemplate() {
    return FRAGMENT_FILE_NAME_TEMPLATE;
  }

  public int getSegmentSize() {
    return segmentSize;
  }

  public String getPath() {
    return path;
  }

  public String fileName(int segmentId) {
    return String.format(FRAGMENT_FILE_NAME_TEMPLATE, path, segmentId);
  }

  public String backupFileName(int segmentId) {
    return String.format(BACKUP_FILE_NAME_TEMPLATE, path, segmentId);
  }

  public String truncatedFileName(int segmentId) {
    return String.format(TRUNCATED_FILE_NAME_TEMPLATE, path, segmentId);
  }

  public boolean matchesFragmentFileNamePattern(File file) {
    return matchesFileNamePattern(file, FRAGMENT_FILE_NAME_PATTERN);
  }

  public boolean matchesBackupFileNamePattern(File file) {
    return matchesFileNamePattern(file, BACKUP_FILE_NAME_PATTERN);
  }

  public boolean matchesTruncatedFileNamePattern(File file) {
    return matchesFileNamePattern(file, TRUNCATED_FILE_NAME_PATTERN);
  }

  protected boolean matchesFileNamePattern(File file, String pattern) {
    return file.getName().matches(pattern);
  }

  public boolean isDeleteOnClose() {
    return deleteOnClose;
  }

  public int getInitialSegmentId() {
    return initialSegmentId;
  }
}
