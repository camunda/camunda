/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET /v1/groups/{group}}: the group's workers and cursors, and the cursors' lag. It answers
 * 404 if the store has no object of the group, and 503 if it can't read the store.
 */
final class GroupHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GroupHandler.class);

  private final GroupView view;

  GroupHandler(final GroupView view) {
    this.view = view;
  }

  void handle(final HttpExchange exchange, final String group) throws IOException {
    final Optional<GroupStatus> status;
    try {
      status = view.read(group);
    } catch (final IOException e) {
      LOG.warn("Expected to read the group '{}' from the store, but got {}", group, e.toString());
      Responses.error(
          exchange,
          503,
          "Expected to read the group '%s' from the store, but got %s".formatted(group, e));
      return;
    }
    if (status.isEmpty()) {
      Responses.error(
          exchange,
          404,
          "Expected the group '%s' to have objects in the store, but found none".formatted(group));
    } else {
      Responses.json(exchange, 200, status.get());
    }
  }
}
