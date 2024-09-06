package io.camunda.service.query;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class QueryParser {

  // Regex to match filters like {$eq:CREATED}
  private static final Pattern FILTER_PATTERN = Pattern.compile("\\{\\$(\\w+):(.+)}");

  public <T> List<Filter>  parse(final T request) {
    final List<Filter> filters = new ArrayList<>();

    // Use reflection to inspect fields of the request object
    for (final Field field : request.getClass().getDeclaredFields()) {
      field.setAccessible(true); // Allow access to private fields

      try {
        final Object value = field.get(request);
        if (value != null && value instanceof String) {
          final String valueStr = (String) value;

          // Try to match the filter pattern (e.g., {$eq:CREATED})
          final Matcher matcher = FILTER_PATTERN.matcher(valueStr);
          if (matcher.matches()) {
            final String operator = matcher.group(1); // Extract the operator (e.g., $eq)
            // must be $eq or $like
            if (!operator.equals("eq") && !operator.equals("like")) {
              throw new IllegalArgumentException("Invalid operator: " + operator);
            }
            final String filterValue = matcher.group(2); // Extract the actual value (e.g., CREATED)
            filters.add(new Filter(field.getName(), operator, filterValue));
          } else {
            // If no operator is found, assume equality
            filters.add(new Filter(field.getName(), "$eq", value));
          }
        }
      } catch (final IllegalAccessException e) {
        e.printStackTrace();
      }
    }

    return filters;
  }
}
