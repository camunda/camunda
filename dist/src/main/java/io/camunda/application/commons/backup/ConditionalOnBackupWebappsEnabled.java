/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.spring.utils.DatabaseTypeUtils;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** This condition is used to enable or disable the webapps backup functionality */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ConditionalOnBackupWebappsEnabled.BackupWebappsEnabledAndDatabaseTypeCondition.class)
public @interface ConditionalOnBackupWebappsEnabled {
  String BACKUP_WEBAPPS_ENABLED = "camunda.backup.webapps.enabled";

  class BackupWebappsEnabledAndDatabaseTypeCondition implements Condition {
    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Environment env = context.getEnvironment();
      final String backupEnabled = env.getProperty(BACKUP_WEBAPPS_ENABLED);
      return "true".equalsIgnoreCase(backupEnabled)
          && DatabaseTypeUtils.isSecondaryStorageEnabled(env);
    }
  }
}
