/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public interface IndexMappingCreator {

  String getIndexName();

  default String getIndexNameSuffix() {
    return "";
  }

  default Boolean getCreateFromTemplate() {
    return false;
  }

  int getVersion();

  XContentBuilder getSource();

  XContentBuilder getCustomSettings(XContentBuilder xContentBuilder) throws IOException;

}
