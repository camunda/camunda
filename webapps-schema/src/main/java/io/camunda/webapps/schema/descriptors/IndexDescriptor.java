/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

import java.util.Optional;
import java.util.OptionalInt;

public interface IndexDescriptor {

  String ID = "id";

  String TENANT_ID = "tenantId";

  String getFullQualifiedName();

  String getAlias();

  /**
   * Returns a comma-separated index expression that covers only the indices holding active data:
   * the main index and any ordinal indices. Dated archive indices are deliberately excluded, so
   * queries over runtime data don't fan out across the historical indices when the archiver is in
   * use.
   *
   * <p>This is a read expression and can match more than one index. Writes must target the concrete
   * index a document was found in.
   */
  default String getActiveNames() {
    final var fullQualifiedName = getFullQualifiedName();
    return fullQualifiedName
        + ","
        + fullQualifiedName
        + IndexTemplateDescriptor.ORDINAL_SUFFIX_START
        + "*";
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
