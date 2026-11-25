/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public interface IndexDescriptor {

  String ID = "id";

  String TENANT_ID = "tenantId";

  String getFullQualifiedName();

  String getAlias();

  /**
   * Returns the index names, possibly with wildcards, that cover only the indices holding active
   * data. Dated archive indices are deliberately excluded, so queries over runtime data don't fan
   * out across the historical indices when the archiver is in use.
   *
   * <p>A plain index only has its main index; {@link IndexTemplateDescriptor} adds its ordinal
   * indices. These are read targets and can match more than one index. Writes must target the
   * concrete index a document was found in.
   */
  default List<String> getActiveIndexNames() {
    return List.of(getFullQualifiedName());
  }

  /**
   * Returns {@link #getActiveIndexNames()} as a single comma-separated index expression, for
   * clients that take one index string.
   */
  default String getActiveNames() {
    return String.join(",", getActiveIndexNames());
  }

  String getIndexName();

  String getComponentName();

  String getMappingsClasspathFilename();

  @Deprecated
  default String getSchemaClasspathFilename() {
    return getMappingsClasspathFilename();
  }

  @Deprecated
  default String getDerivedIndexNamePattern() {
    return getFullQualifiedName() + "*";
  }

  String getAllVersionsIndexNameRegexPattern();

  String getIndexNameWithoutVersion();

  String getVersion();

  default OptionalInt getDefaultShardCount() {
    return OptionalInt.empty();
  }

  default boolean allowMissing() {
    return false;
  }

  default Optional<String> getTenantIdField() {
    return Optional.empty();
  }

  default String getIdField() {
    return ID;
  }
}
