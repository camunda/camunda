/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * A set of files with names. Files are represented by their paths and names can be arbitrary
 * strings, for example the actual file name (last part of the path) or a pre-defined name that is
 * unrelated to the path.
 */
public interface NamedFileSet {

  /**
   * @return the names of files contained in this set
   */
  Set<String> names();

  /**
   * @return the paths to files contained in this set
   */
  Set<Path> files();

  /**
   * @return a map from file names to file paths
   */
  Map<String, Path> namedFiles();
}
