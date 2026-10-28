/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.configuration.data.secondarystorage.Rdbms;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * The connection settings of a physical tenant's database, each either as configured (a literal or
 * a {@code camunda.secrets.<name>} reference) or resolved.
 */
@NullMarked
record RdbmsConnection(String url, String username, String password) {

  static final String URL = "data.secondary-storage.rdbms.url";
  static final String USERNAME = "data.secondary-storage.rdbms.username";
  static final String PASSWORD = "data.secondary-storage.rdbms.password";

  static RdbmsConnection configured(final Rdbms rdbms) {
    return new RdbmsConnection(
        Objects.requireNonNullElse(rdbms.getUrl(), ""),
        Objects.requireNonNullElse(rdbms.getUsername(), ""),
        Objects.requireNonNullElse(rdbms.getPassword(), ""));
  }

  static RdbmsConnection of(final Map<String, String> values) {
    return new RdbmsConnection(
        Objects.requireNonNullElse(values.get(URL), ""),
        Objects.requireNonNullElse(values.get(USERNAME), ""),
        Objects.requireNonNullElse(values.get(PASSWORD), ""));
  }

  Map<String, String> values() {
    final Map<String, String> values = new LinkedHashMap<>();
    values.put(URL, url);
    values.put(USERNAME, username);
    values.put(PASSWORD, password);
    return values;
  }

  @Override
  public String toString() {
    return "RdbmsConnection[url=" + url + ", username=" + username + ", password=***]";
  }
}
