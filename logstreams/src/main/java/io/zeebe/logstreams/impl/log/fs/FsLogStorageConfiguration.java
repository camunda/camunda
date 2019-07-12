/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
