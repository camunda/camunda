/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.mapper;

import io.camunda.security.validation.IdentifierValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Derives the server-assigned Camunda group id from the SCIM {@code displayName}: lowercased, every
 * run of characters the group id validation rejects replaced by {@code -}, trimmed of {@code -}. An
 * empty result falls back to the {@code externalId} sanitized the same way, then to a random UUID.
 * Whether a character is allowed is asked of the {@link IdentifierValidator} itself, so the
 * configured id pattern is honoured instead of being re-declared here.
 */
public class ScimGroupIdGenerator {

  private static final int MAX_LENGTH = 256;
  private static final char SEPARATOR = '-';

  private final IdentifierValidator identifierValidator;

  public ScimGroupIdGenerator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  public String generate(final String displayName, final String externalId) {
    final var fromName = slug(displayName);
    if (!fromName.isEmpty()) {
      return fromName;
    }
    final var fromExternalId = slug(externalId);
    if (!fromExternalId.isEmpty()) {
      return fromExternalId;
    }
    return UUID.randomUUID().toString();
  }

  private String slug(final String source) {
    if (source == null) {
      return "";
    }
    final var builder = new StringBuilder();
    source
        .toLowerCase(Locale.ROOT)
        .codePoints()
        .mapToObj(Character::toString)
        .forEach(
            character -> {
              if (isAllowed(character)) {
                builder.append(character);
              } else if (builder.isEmpty() || builder.charAt(builder.length() - 1) != SEPARATOR) {
                builder.append(SEPARATOR);
              }
            });
    var start = 0;
    var end = Math.min(builder.length(), MAX_LENGTH);
    while (start < end && builder.charAt(start) == SEPARATOR) {
      start++;
    }
    while (end > start && builder.charAt(end - 1) == SEPARATOR) {
      end--;
    }
    final var candidate = builder.substring(start, end);
    return isAllowed(candidate) ? candidate : "";
  }

  private boolean isAllowed(final String value) {
    if (value.isEmpty()) {
      return false;
    }
    final List<String> violations = new ArrayList<>();
    identifierValidator.validateGroupId(value, violations);
    return violations.isEmpty();
  }
}
