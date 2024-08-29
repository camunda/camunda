/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QueryParser {

  public <T> List<Filter> parse(final T request) {
    final List<Filter> filters = new ArrayList<>();

    // Use reflection to inspect fields of the request object
    for (final Field field : request.getClass().getDeclaredFields()) {
      field.setAccessible(true); // Allow access to private fields

      try {
        final Object value = field.get(request);
        if (value != null) {
          // Assuming equality for simple fields; you can enhance this with more logic
          filters.add(new Filter(field.getName(), "$eq", value));
        }
      } catch (final IllegalAccessException e) {
        e.printStackTrace();
      }
    }

    return filters;
  }
}
