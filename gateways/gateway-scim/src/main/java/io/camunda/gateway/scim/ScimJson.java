/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.utils.JsonUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.node.ObjectNode;

/** Reads and writes SCIM payloads with the SDK's own mapper, which knows the SCIM models. */
public final class ScimJson {

  public static final String MEDIA_TYPE = "application/scim+json";

  private ScimJson() {}

  public static <T> T read(final String body, final Class<T> type) throws BadRequestException {
    if (body == null || body.isBlank()) {
      throw BadRequestException.invalidSyntax("Request body is missing");
    }
    try {
      return JsonUtils.getObjectReader().forType(type).readValue(body);
    } catch (final JacksonException e) {
      throw BadRequestException.invalidSyntax(
          "Request body cannot be parsed: " + e.getOriginalMessage());
    }
  }

  public static ObjectNode newObject() {
    return JsonUtils.getJsonNodeFactory().objectNode();
  }

  public static String write(final Object value) {
    return JsonUtils.getObjectWriter().writeValueAsString(value);
  }
}
