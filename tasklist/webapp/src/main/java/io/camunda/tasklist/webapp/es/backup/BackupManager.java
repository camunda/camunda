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
import io.camunda.tasklist.schema.backup.Prio3Backup;
import io.camunda.tasklist.schema.backup.Prio4Backup;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupResponseDto;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BackupManager {
  // Match all numbers, optionally ending with a *
  static Pattern backupIdPattern = Pattern.compile("^(\\d*)\\*?$");

  @Autowired private List<Prio1Backup> prio1BackupIndices;
  @Autowired private List<Prio2Backup> prio2BackupTemplates;
  @Autowired private List<Prio3Backup> prio3BackupTemplates;
  @Autowired private List<Prio4Backup> prio4BackupIndices;
  @Autowired private TasklistProperties tasklistProperties;

  private String[][] indexPatternsOrdered;

  public abstract void deleteBackup(Long backupId);

  public abstract TakeBackupResponseDto takeBackup(TakeBackupRequestDto request);

  public abstract GetBackupStateResponseDto getBackupState(Long backupId);

  public List<GetBackupStateResponseDto> getBackups() {
    return getBackups(true, null);
  }

  public abstract List<GetBackupStateResponseDto> getBackups(
      final boolean verbose, final String pattern);

  public static Either<Throwable, String> validPattern(final String pattern) {
    if (pattern == null || pattern.isEmpty()) {
      return Either.right("*");
    } else if (pattern.length() <= 20 && backupIdPattern.matcher(pattern).matches()) {
      return Either.right(pattern);
    } else {
      return Either.left(new IllegalArgumentException("Invalid pattern: " + pattern));
    }
  }

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
            prio1BackupIndices.stream().map(this::getFullQualifiedName).toArray(String[]::new),
            prio2BackupTemplates.stream().map(this::getFullQualifiedName).toArray(String[]::new),
            // dated indices
            prio2BackupTemplates.stream()
                .filter(i -> i instanceof TemplateDescriptor)
                .map(
                    index ->
                        new String[] {
                          getFullQualifiedName(index) + "*", "-" + getFullQualifiedName(index)
                        })
                .flatMap(x -> Arrays.stream(x))
                .toArray(String[]::new),
            prio3BackupTemplates.stream().map(this::getFullQualifiedName).toArray(String[]::new),
            // dated indices
            prio3BackupTemplates.stream()
                .filter(i -> i instanceof TemplateDescriptor)
                .map(
                    index ->
                        new String[] {
                          getFullQualifiedName(index) + "*", "-" + getFullQualifiedName(index)
                        })
                .flatMap(Arrays::stream)
                .toArray(String[]::new),
            prio4BackupIndices.stream().map(this::getFullQualifiedName).toArray(String[]::new),
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
