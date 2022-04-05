/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema;

public interface Versionable {

  String DEFAULT_SCHEMA_VERSION = "1.0.0";

  default String getVersion() {
    return DEFAULT_SCHEMA_VERSION;
  }

}
