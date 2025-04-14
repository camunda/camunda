/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process.util;

import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import io.camunda.zeebe.util.modelreader.ProcessModelReader.StartFormLink;
import java.util.regex.Pattern;

public class MigrationUtil {

  public static final Pattern MIGRATION_REPOSITORY_NOT_EXISTS =
      Pattern.compile(
          "no such index \\[[a-zA-Z0-9\\-]+-migration-steps-repository-[0-9]+\\.[0-9]+\\.[0-9]+_]");

  public static ProcessEntity migrate(final ProcessEntity entity) {
    final ProcessEntity processEntity = new ProcessEntity();
    processEntity.setId(entity.getId());
    processEntity.setBpmnProcessId(entity.getBpmnProcessId());

    ProcessModelReader.of(entity.getBpmnXml().getBytes(), entity.getBpmnProcessId())
        .ifPresent(
            reader -> {
              reader
                  .extractStartFormLink()
                  .ifPresentOrElse(
                      formLink -> {
                        processEntity.setFormId(formLink.formId());
                        processEntity.setFormKey(formLink.formKey());
                        processEntity.setIsFormEmbedded(isFormEmbedded(formLink));
                      },
                      () -> processEntity.setIsFormEmbedded(false));
              processEntity.setIsPublic(reader.extractIsPublicAccess());
            });
    return processEntity;
  }

  private static Boolean isFormEmbedded(final StartFormLink formLink) {
    return formLink.formKey() != null && !formLink.formKey().isEmpty();
  }
}
