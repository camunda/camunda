/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.el;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import java.util.ArrayList;
import java.util.List;

public class JsonConditionValidator {

  public static String validate(JsonCondition condition) {
    final List<String> errors = new ArrayList<>();

    validateCondition(condition, errors);

    if (errors.isEmpty()) {
      return null;
    } else {
      return formatErrorMessage(errors);
    }
  }

  private static void validateCondition(JsonCondition condition, final List<String> errors) {
    JsonConditionWalker.walk(
        condition,
        object -> {
          if (object instanceof JsonPath) {
            final JsonPath path = (JsonPath) object;
            final JsonPathQuery query = path.query();

            if (!query.isValid()) {
              errors.add(query.getErrorReason());
            }
          }
        });
  }

  private static String formatErrorMessage(final List<String> errors) {
    final StringBuilder builder = new StringBuilder();

    builder.append(errors.get(0));

    for (int i = 1; i < errors.size(); i++) {
      final String error = errors.get(i);

      builder.append("\n");
      builder.append(error);
    }

    return builder.toString();
  }
}
