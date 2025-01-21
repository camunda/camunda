/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.localstorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

class FileHandler {

  public InputStream getInputStream(final Path documentPath) throws IOException {
    return Files.newInputStream(documentPath);
  }

  public void createFile(final InputStream stream, final Path path) throws IOException {
    Files.copy(stream, path);
  }

  public boolean fileExists(final Path path) {
    return Files.exists(path);
  }

  public void delete(final Path path) throws IOException {
    Files.deleteIfExists(path);
  }
}
