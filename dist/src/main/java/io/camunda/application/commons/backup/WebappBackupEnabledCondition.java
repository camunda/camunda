/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.application.commons.conditions.WebappEnabledCondition;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

/** This condition is used to enable or disable the webapps backup functionality */
public class WebappBackupEnabledCondition extends AnyNestedCondition {

  public static final String BACKUP_WEBAPPS_ENABLED = "camunda.backup.webapps.enabled";

  public WebappBackupEnabledCondition() {
    super(ConfigurationPhase.PARSE_CONFIGURATION);
  }

  /** This condition checks if the webapps are enabled in the application. */
  @Conditional(WebappEnabledCondition.class)
  static class WebappEnabled {}

  /**
   * This condition force-enables the webapps backup functionality if the property is set to true.
   * This is used by the standalone backup manager app.
   */
  @ConditionalOnProperty(prefix = "camunda.backup.webapps", name = "enabled", havingValue = "true")
  static class BackupWebappsEnabledCondition {}
}
