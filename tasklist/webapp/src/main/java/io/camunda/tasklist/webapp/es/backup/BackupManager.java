/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.backup;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.backup.BackupPriority;
import io.camunda.tasklist.schema.backup.Prio1Backup;
import io.camunda.tasklist.schema.backup.Prio2Backup;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
import io.camunda.webapps.backup.BackupService;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BackupManager implements BackupService {

  @Autowired private List<Prio1Backup> prio1BackupTemplates;
  @Autowired private List<Prio2Backup> prio2BackupIndices;
  @Autowired private TasklistProperties tasklistProperties;

  private String[][] indexPatternsOrdered;

  protected String getFullQualifiedName(final BackupPriority index) {
    if (index instanceof IndexDescriptor) {
      return ((IndexDescriptor) index).getFullQualifiedName();
    } else if (index instanceof TemplateDescriptor) {
      return ((TemplateDescriptor) index).getFullQualifiedName();
    } else {
      throw new TasklistRuntimeException("Can't find out index name for backup.");
    }
  }

  protected String[][] getIndexPatternsOrdered() {
    if (indexPatternsOrdered == null) {
      indexPatternsOrdered =
          new String[][] {
            prio1BackupTemplates.stream().map(this::getFullQualifiedName).toArray(String[]::new),
            // dated indices
            prio1BackupTemplates.stream()
                .filter(i -> i instanceof TemplateDescriptor)
                .map(
                    index ->
                        new String[] {
                          getFullQualifiedName(index) + "*", "-" + getFullQualifiedName(index)
                        })
                .flatMap(x -> Arrays.stream(x))
                .toArray(String[]::new),
            prio2BackupIndices.stream().map(this::getFullQualifiedName).toArray(String[]::new)
          };
    }
    return indexPatternsOrdered;
  }

  protected String getRepositoryName() {
    return tasklistProperties.getBackup().getRepositoryName();
  }

  protected String getCurrentTasklistVersion() {
    return tasklistProperties.getVersion().toLowerCase();
  }
}
