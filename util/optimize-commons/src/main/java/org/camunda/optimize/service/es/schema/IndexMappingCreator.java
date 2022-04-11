/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public interface IndexMappingCreator {

  String getIndexName();

  default String getIndexNameInitialSuffix() {
    return "";
  }

  default boolean isCreateFromTemplate() {
    return false;
  }

  int getVersion();

  XContentBuilder getSource();

  XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                    ConfigurationService configurationService) throws IOException;

}
