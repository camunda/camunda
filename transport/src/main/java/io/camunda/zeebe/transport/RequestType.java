/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport;

/**
 * Defines the supported types of request that can be sent through the transport. A subscribed
 * request handler will only receive requests of the request types it subscribed to.
 *
 * <p>Normally describing the supported request types should not be a transport concern, but an
 * application concern. However, having it as part of the transport makes unsubscribing from all
 * possible request types easier. Sending and handling a new request type in the application will
 * also require adding it here.
 */
public enum RequestType {
  // Supported request types
  COMMAND("command"),
  QUERY("query"),
  ADMIN("admin"),

  // All other request types are considered unknown
  // This value exists mainly for testing purposes
  UNKNOWN("unknown");

  private final String id;

  RequestType(final String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }
}
