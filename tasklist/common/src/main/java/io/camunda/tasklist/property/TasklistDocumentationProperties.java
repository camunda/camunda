/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

public class TasklistDocumentationProperties {

  private String apiMigrationDocsUrl =
      "https://docs.camunda.io/docs/apis-tools/tasklist-api-rest/migrate-to-zeebe-user-tasks/";

  public String getApiMigrationDocsUrl() {
    return apiMigrationDocsUrl;
  }

  public void setApiMigrationDocsUrl(final String apiMigrationDocsUrl) {
    this.apiMigrationDocsUrl = apiMigrationDocsUrl;
  }
}
