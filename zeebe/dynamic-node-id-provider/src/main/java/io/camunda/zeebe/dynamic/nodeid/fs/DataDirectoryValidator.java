/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.fs;

import java.io.IOException;
import java.nio.file.Path;

/** Validates that a data directory has been correctly copied/initialized. */
public interface DataDirectoryValidator {

  /**
   * Validates that the target directory has been correctly initialized from the source directory.
   *
   * @param source the source data directory that was copied from
   * @param target the target data directory that was copied to
   * @param markerFileName a marker file name that was not copied
   * @throws IOException if validation fails
   */
  void validate(Path source, Path target, String markerFileName) throws IOException;
}
