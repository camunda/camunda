/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.implement;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexTemplateDescriptorsConfigurator;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.TasklistIndexTemplateDescriptorsConfigurator;
import io.camunda.webapps.schema.descriptors.backup.BackupPriority;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      BackupPrioritiesTest.TestBackupPriorityConfiguration.class,
      BackupPriorityConfiguration.class,
      // Classes that provide the Beans for the indices
      TasklistIndexTemplateDescriptorsConfigurator.class,
      IndexTemplateDescriptorsConfigurator.class,
      UserManagementIndicesConfig.class
    })
abstract class BackupPrioritiesTest {

  @Autowired BackupPriorityConfiguration backupPriorityConfiguration;

  private Stream<JavaClass> allImplementations() {
    final var implementations =
        new ClassFileImporter()
                .importPackages("io.camunda.webapps.schema")
                .that(implement(BackupPriority.class))
                .stream();
    final var optimizeImplementations =
        new ClassFileImporter()
                .importPackages("io.camunda.optimize.service.db.schema.index")
                .that(implement(BackupPriority.class))
                .stream();

    return Stream.concat(implementations, optimizeImplementations);
  }

  @Test
  public void onlyOneInterfaceIsImplemented() {
    assertThat(allImplementations().toList())
        .allSatisfy(
            clazz ->
                assertThat(
                        clazz.getInterfaces().stream()
                            .filter(i -> i.getName().matches(".*.Prio\\d+Backup"))
                            .count())
                    .isEqualTo(1));
  }

  @Test
  public void testBackupPriorities() {
    final var priorities = backupPriorityConfiguration.backupPriorities();

    final Set<String> allPriorities =
        priorities.allPriorities().map(obj -> obj.getClass().getName()).collect(Collectors.toSet());

    final var classes = allImplementations().toList();

    final var missingInBackup = new HashSet<String>();
    classes.forEach(
        clzz -> {
          if (!allPriorities.contains(clzz.getName())) {
            missingInBackup.add(clzz.getName());
          }
        });
    assertThat(missingInBackup).isEmpty();
  }

  @TestConfiguration
  static class TestBackupPriorityConfiguration {

    @Bean
    public ConnectConfiguration connectConfiguration() {
      return new ConnectConfiguration();
    }

    @Primary
    @Bean
    public OperateProperties properties() {
      return new OperateProperties();
    }

    @Primary
    @Bean
    public TasklistProperties tasklistProperties() {
      return new TasklistProperties();
    }

    @Primary
    @Bean
    DatabaseInfo databaseInfo() {
      return new DatabaseInfo();
    }
  }
}
