/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import io.camunda.zeebe.auth.Authorization;
import java.util.Map;

/**
 * Who asks for a change: whoever the gateway authenticated on the command that created the process
 * instance. The gateway builds the auth info from the token it validated, never from the request
 * body, so no caller can set it.
 */
public final class Requester {

  /** A client is not a person, so its id is kept apart from usernames. */
  public static final String CLIENT_PREFIX = "client:";

  private Requester() {}

  /** Returns the requester of a command, or an empty string when the command has no caller. */
  public static String of(final Map<String, Object> authorizations) {
    if (authorizations == null) {
      return "";
    }
    final Object username = authorizations.get(Authorization.AUTHORIZED_USERNAME);
    if (username instanceof final String name && !name.isBlank()) {
      return name;
    }
    final Object clientId = authorizations.get(Authorization.AUTHORIZED_CLIENT_ID);
    if (clientId instanceof final String id && !id.isBlank()) {
      return CLIENT_PREFIX + id;
    }
    return "";
  }

  /** Returns whether the requester is a person who signed in, rather than a client or nobody. */
  public static boolean isPerson(final String requester) {
    return requester != null && !requester.isBlank() && !requester.startsWith(CLIENT_PREFIX);
  }
}
