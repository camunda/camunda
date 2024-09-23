/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;

public interface IndexMappingCreator<TBuilder> {

  String getIndexName();

  default String getIndexNameInitialSuffix() {
    return "";
  }

  default boolean isCreateFromTemplate() {
    return false;
  }

  default boolean isImportIndex() {
    return false;
  }

  int getVersion();

  TypeMapping getSource();

  TBuilder getStaticSettings(TBuilder builder, ConfigurationService configurationService)
      throws IOException;
}
