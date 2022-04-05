/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.templates;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TaskVariableTemplate extends AbstractTemplateDescriptor {

  public static final String INDEX_NAME = "task-variable";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String TASK_ID = "taskId";
  public static final String NAME = "name";
  public static final String VALUE = "value";
  public static final String FULL_VALUE = "fullValue";
  public static final String IS_PREVIEW = "isPreview";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  private static Optional<String> getElsFieldByGraphqlField(String fieldName) {
    switch (fieldName) {
      case ("id"):
        return of(ID);
      case ("name"):
        return of(NAME);
      case ("value"):
        return of(FULL_VALUE);
      case ("previewValue"):
        return of(VALUE);
      case ("isValueTruncated"):
        return of(IS_PREVIEW);
      default:
        return empty();
    }
  }

  public static Set<String> getElsFieldsByGraphqlFields(Set<String> fieldNames) {
    return fieldNames.stream()
        .map((fn) -> getElsFieldByGraphqlField(fn))
        .flatMap(Optional::stream)
        .collect(Collectors.toSet());
  }

  @Override
  public String getVersion() {
    return "1.1.0";
  }
}
