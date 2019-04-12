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
  private static final String FRAGMENT_FILE_NAME_TEMPLATE = "%s" + File.separatorChar + "%02d.data";
  private static final String FRAGMENT_FILE_NAME_PATTERN = "\\d+.data";

  private final int segmentSize;
  private final String path;
  private final int initialSegmentId;
  private final boolean deleteOnClose;

  public FsLogStorageConfiguration(
      int segmentSize, String path, int initialSegmentId, boolean deleteOnClose) {
    this.segmentSize = segmentSize;
    this.path = path;
    this.initialSegmentId = initialSegmentId;
    this.deleteOnClose = deleteOnClose;
  }

  int getSegmentSize() {
    return segmentSize;
  }

  public String getPath() {
    return path;
  }

  public String fileName(int segmentId) {
    return String.format(FRAGMENT_FILE_NAME_TEMPLATE, path, segmentId);
  }

  boolean matchesFragmentFileNamePattern(File file) {
    return matchesFileNamePattern(file, FRAGMENT_FILE_NAME_PATTERN);
  }

  private boolean matchesFileNamePattern(File file, String pattern) {
    return file.getName().matches(pattern);
  }

  boolean isDeleteOnClose() {
    return deleteOnClose;
  }

  public int getInitialSegmentId() {
    return initialSegmentId;
  }
}
