/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.secondarystorage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for configuration of databases used as secondary storage.
 *
 * <p>Note: For now there only is support for document-based databases, but in the future also
 * relational databases will be supported.
 *
 * @param <T> the type of history configuration this database supports.
 */
public abstract class SecondaryStorageDatabase<T> {

  /**
   * Endpoint for the database configured as secondary storage. Mutually exclusive with {@link
   * #urls} - configure only one.
   */
  private String url = "http://localhost:9200";

  /**
   * List of endpoints for the database configured as secondary storage. Use for multi-node cluster
   * connectivity. Mutually exclusive with {@link #url} - configure only one.
   */
  private List<String> urls = new ArrayList<>();

  /** Username for the database configured as secondary storage. */
  private String username = "";

  /** Password for the database configured as secondary storage. */
  private String password = "";

  /**
   * How long a node keeps the clients, connections and mappers it created for this database after
   * their last use. After that, it closes them and creates them again on the next use. Set to 0 to
   * keep them until the node stops.
   */
  private Duration idleReleaseTimeout = Duration.ofMinutes(10);

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public List<String> getUrls() {
    return urls;
  }

  public void setUrls(final List<String> urls) {
    this.urls = urls;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public Duration getIdleReleaseTimeout() {
    return idleReleaseTimeout;
  }

  public void setIdleReleaseTimeout(final Duration idleReleaseTimeout) {
    this.idleReleaseTimeout = idleReleaseTimeout;
  }

  public abstract T getHistory();

  public abstract void setHistory(final T history);

  public abstract String databaseName();
}
