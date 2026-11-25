/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

final class TerminationMessage {

  private TerminationMessage() {}

  static void write(final Path path, final List<Group> groups) throws IOException {
    // group ids are fixed lower-case words, so no JSON escaping is needed
    final var ids = groups.stream().map(g -> '"' + g.id() + '"').collect(Collectors.joining(","));
    Files.writeString(path, "{\"groups\":[" + ids + "]}");
  }
}
