/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.indices;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import io.camunda.tasklist.schema.v86.backup.Prio3Backup;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class VariableIndex extends AbstractIndexDescriptor
    implements ProcessInstanceDependant, Prio3Backup {

  public static final String INDEX_NAME = "variable";
  public static final String INDEX_VERSION = "8.3.0";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String SCOPE_FLOW_NODE_ID = "scopeFlowNodeId";
  public static final String NAME = "name";
  public static final String VALUE = "value";
  public static final String FULL_VALUE = "fullValue";
  public static final String IS_PREVIEW = "isPreview";
  public static final String TENANT_ID = "tenantId";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getAllIndicesPattern() {
    return getFullQualifiedName();
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
    return INDEX_VERSION;
  }
}
