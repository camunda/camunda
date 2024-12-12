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
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.backup.BackupPriority;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      BackupPrioritiesTest.TestBackupPriorityConfiguration.class,
      BackupPriorityConfiguration.class,
    })
abstract class BackupPrioritiesTest {

  static final List<JavaClass> ALL_IMPLEMENTATIONS;

  static {
    ALL_IMPLEMENTATIONS =
        new ClassFileImporter()
                .importPackages(
                    // TODO ADD optimize
                    "io.camunda.webapps.schema") // , "io.camunda.optimize.service.db.schema.index")
                .that(implement(BackupPriority.class))
                .stream()
                .filter(clz -> !clz.getName().contains("Abstract"))
                .filter(
                    // DISCARD OPTIMIZE classes that don't have an empty constructor
                    clz -> {
                      final var hasEmptyConstructor =
                          clz.getConstructors().stream()
                                  .filter(ctor -> ctor.getParameters().isEmpty())
                                  .count()
                              == 1;
                      final var isOptimizeIndex = clz.getPackage().getName().contains("optimize");
                      if (isOptimizeIndex) {
                        return hasEmptyConstructor;
                      }
                      return true;
                    })
                .toList();
  }

  @Autowired BackupPriorityConfiguration backupPriorityConfiguration;

  @Test
  public void allImplementationsContainIndicesFromAllApps() {
    final var operate =
        ALL_IMPLEMENTATIONS.stream()
            .filter(clz -> clz.getPackage().getName().contains("operate"))
            .toList();
    assertThat(operate).isNotEmpty();
    final var tasklist =
        ALL_IMPLEMENTATIONS.stream()
            .filter(clz -> clz.getPackage().getName().contains("tasklist"))
            .toList();
    assertThat(tasklist).isNotEmpty();
    // TODO add optimize
    //    final var optimize =
    //        allImplementations.stream()
    //            .filter(clz -> clz.getPackage().getName().contains("optimize"))
    //            .toList();
    //    assertThat(optimize).isNotEmpty();
  }

  @Test
  public void onlyOneInterfaceIsImplemented() {
    assertThat(ALL_IMPLEMENTATIONS)
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

    final var missingInBackup = new HashSet<String>();
    ALL_IMPLEMENTATIONS.forEach(
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

    //    @Primary
    //    @Bean
    //    public OperateProperties properties() {
    //      return new OperateProperties();
    //    }
    //
    //    @Primary
    //    @Bean
    //    public TasklistProperties tasklistProperties() {
    //      return new TasklistProperties();
    //    }
    //
    //    @Primary
    //    @Bean
    //    DatabaseInfo databaseInfo() {
    //      return new DatabaseInfo();
    //    }
  }
}
