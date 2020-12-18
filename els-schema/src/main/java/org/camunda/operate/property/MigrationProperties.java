/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
@ConfigurationProperties(OperateProperties.PREFIX + ".migration")
public class MigrationProperties {

  private boolean migrationEnabled = true;
  private boolean deleteSrcSchema = true;

  private String sourceVersion;
  private String destinationVersion;

  public boolean isMigrationEnabled() {
    return migrationEnabled;
  }

  public MigrationProperties setMigrationEnabled(boolean migrationEnabled) {
    this.migrationEnabled = migrationEnabled;
    return this;
  }

  public String getSourceVersion() {
    return sourceVersion;
  }

  public MigrationProperties setSourceVersion(String sourceVersion) {
    this.sourceVersion = sourceVersion;
    return this;
  }

  public String getDestinationVersion() {
    return destinationVersion;
  }

  public MigrationProperties setDestinationVersion(String destinationVersion) {
    this.destinationVersion = destinationVersion;
    return this;
  }

  public MigrationProperties setDeleteSrcSchema(boolean deleteSrcSchema){
    this.deleteSrcSchema = deleteSrcSchema;
    return this;
  }

  public boolean isDeleteSrcSchema() {
    return deleteSrcSchema;
  }
}
