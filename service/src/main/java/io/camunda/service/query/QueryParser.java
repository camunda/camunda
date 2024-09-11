/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import io.camunda.service.query.filter.Filter;
import io.camunda.service.query.filter.FilterOperator;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class QueryParser {

  // Regex to match filters like {$eq:CREATED}, {$gte:123}, {$in:[CREATED,COMPLETED]}
  private static final Pattern FILTER_PATTERN = Pattern.compile("\\{\\$(\\w+):(.+)}");

  public <T> List<Filter> parse(final T request) {
    final List<Filter> filters = new ArrayList<>();

    // Use reflection to inspect fields of the request object
    for (final Field field : request.getClass().getDeclaredFields()) {
      field.setAccessible(true); // Allow access to private fields

      try {
        final Object value = field.get(request);
        if (value != null) {
          if (value instanceof final String valueStr) {

            // Try to match the filter pattern (e.g., {$eq:CREATED}, {$in:[CREATED,COMPLETED]})
            final Matcher matcher = FILTER_PATTERN.matcher(valueStr);
            if (matcher.matches()) {
              final String operatorStr = matcher.group(1); // Extract the operator (e.g., $eq)
              final FilterOperator operator;

              try {
                // Use the FilterOperator enum to validate and parse the operator
                operator = FilterOperator.fromString(operatorStr);
              } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported operator: " + operatorStr);
              }

              final String filterValue = matcher.group(2); // Extract the actual value (e.g., CREATED or [CREATED,COMPLETED])

              // Handle $exists operator
              if (FilterOperator.EXISTS.equals(operator)) {
                // Handle the case for $exists being true/false
                if (!"true".equalsIgnoreCase(filterValue) && !"false".equalsIgnoreCase(filterValue)) {
                  throw new IllegalArgumentException("Invalid value for $exists operator: " + filterValue);
                }
                filters.add(new Filter(field.getName(), operator, Boolean.parseBoolean(filterValue)));

                // Handle $in operator (list of values)
              } else if (FilterOperator.IN.equals(operator)) {
                if (!filterValue.startsWith("[") || !filterValue.endsWith("]")) {
                  throw new IllegalArgumentException("Invalid list format for $in operator: " + filterValue);
                }
                final String listValue = filterValue.replaceAll("[\\[\\]]", "");  // Remove square brackets
                final List<String> values = Arrays.asList(listValue.split(","));
                filters.add(new Filter(field.getName(), operator, values));  // Store the list of values

                // Handle other operators ($eq, $gte, etc.)
              } else {
                filters.add(new Filter(field.getName(), operator, filterValue));
              }
            } else {
              // If no operator is found, assume $eq by default
              filters.add(new Filter(field.getName(), FilterOperator.EQ, valueStr));
            }
          } else {
            // Handle non-string field types (for numbers, dates, etc.)
            filters.add(new Filter(field.getName(), FilterOperator.EQ, value));
          }
        }
      } catch (final IllegalAccessException e) {
        e.printStackTrace();
      }
    }

    return filters;
  }
}
