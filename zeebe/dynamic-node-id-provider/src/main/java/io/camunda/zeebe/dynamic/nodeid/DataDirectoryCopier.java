/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import java.io.IOException;
import java.nio.file.Path;

public interface DataDirectoryCopier {

  /**
   * Copies the contents of {@code source} to {@code target}.
   *
   * @param markerFileName a marker file name that should not be copied
   */
  void copy(Path source, Path target, String markerFileName) throws IOException;

  void validate(final Path source, final Path target, final String markerFileName)
      throws IOException;
}
