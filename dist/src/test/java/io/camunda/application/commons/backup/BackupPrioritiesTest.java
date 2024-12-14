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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.schema.descriptors.backup.BackupPriority;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BackupPrioritiesTest {

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

  public static Stream<Object[]> properties() {
    return Stream.of(null, new OperateProperties())
        .flatMap(
            op ->
                Stream.of(null, new TasklistProperties())
                    .map(tasklist -> new Object[] {op, tasklist}))
        // At least one property must be configured
        .filter(arr -> !Arrays.stream(arr).allMatch(Objects::isNull));
  }

  @MethodSource("properties")
  @ParameterizedTest
  public void testBackupPriorities(
      final OperateProperties operateProperties, final TasklistProperties tasklistProperties) {
    final var configuration =
        new BackupPriorityConfiguration(operateProperties, tasklistProperties);
    final var priorities = configuration.backupPriorities();

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

  @Test
  public void testBackupPrioritiesIndicesSplitBySnapshot() {
    final var configuration = new BackupPriorityConfiguration(new OperateProperties(), null);
    final var priorities = configuration.backupPriorities();

    final var indices = priorities.indicesSplitBySnapshot().toList();

    assertThat(indices.size()).isEqualTo(8);
    // PRIO 1
    assertThat(indices.get(0).indices())
        .containsExactlyInAnyOrder(
            "operate-import-position-8.3.0_",
            "tasklist-import-position-8.2.0_"); // ADD optimize-position-based-import-index_v3)
    // PRIO 2
    assertThat(indices.get(1).indices())
        .containsExactlyInAnyOrder("operate-list-view-8.3.0_", "tasklist-task-8.5.0_");
    // PRIO 2 TEMPLATES
    assertThat(indices.get(2).indices())
        .containsExactlyInAnyOrder(
            "operate-list-view-8.3.0_*",
            "-operate-list-view-8.3.0_",
            "tasklist-task-8.5.0_*",
            "-tasklist-task-8.5.0_");
    // PRIO 3
    assertThat(indices.get(3).indices())
        .containsExactlyInAnyOrder("operate-batch-operation-1.0.0_", "operate-operation-8.4.1_");
    // PRIO 4
    assertThat(indices.get(4).indices())
        .containsExactlyInAnyOrder(
            "operate-decision-8.3.0_",
            "operate-decision-instance-8.3.0_",
            "operate-event-8.3.0_",
            "operate-flownode-instance-8.3.1_",
            "operate-job-8.6.0_",
            "operate-incident-8.3.1_",
            "operate-message-8.5.0_",
            "operate-post-importer-queue-8.3.0_",
            "operate-sequence-flow-8.3.0_",
            "operate-user-task-8.5.0_",
            "operate-variable-8.3.0_",
            "tasklist-draft-task-variable-8.3.0_",
            "tasklist-task-variable-8.3.0_");

    // PRIO 4 TEMPLATES
    assertThat(indices.get(5).indices())
        .containsExactlyInAnyOrder(
            "operate-event-8.3.0_*",
            "-operate-event-8.3.0_",
            "operate-flownode-instance-8.3.1_*",
            "-operate-flownode-instance-8.3.1_",
            "operate-incident-8.3.1_*",
            "-operate-incident-8.3.1_",
            "operate-job-8.6.0_*",
            "-operate-job-8.6.0_",
            "operate-message-8.5.0_*",
            "-operate-message-8.5.0_",
            "operate-post-importer-queue-8.3.0_*",
            "-operate-post-importer-queue-8.3.0_",
            "operate-sequence-flow-8.3.0_*",
            "-operate-sequence-flow-8.3.0_",
            "operate-user-task-8.5.0_*",
            "-operate-user-task-8.5.0_",
            "operate-variable-8.3.0_*",
            "-operate-variable-8.3.0_",
            "-operate-decision-instance-8.3.0_",
            "operate-decision-instance-8.3.0_*",
            "-tasklist-draft-task-variable-8.3.0_",
            "tasklist-draft-task-variable-8.3.0_*",
            "-tasklist-task-variable-8.3.0_",
            "tasklist-task-variable-8.3.0_*");

    // PRIO 5
    assertThat(indices.get(6).indices())
        .containsExactlyInAnyOrder(
            "operate-decision-requirements-8.3.0_",
            "operate-metric-8.3.0_",
            "operate-process-8.3.0_",
            "tasklist-form-8.4.0_",
            "tasklist-metric-8.3.0_",
            "camunda-authorization-8.7.0_",
            "camunda-group-8.7.0_",
            "camunda-mapping-8.7.0_",
            "camunda-web-session-8.7.0_",
            "camunda-role-8.7.0_",
            "camunda-tenant-8.7.0_",
            "camunda-user-8.7.0_");
  }

  @Test
  public void shouldFailIfIndexPrefixIsDifferent() {
    final var operateProperties = new OperateProperties();
    operateProperties.getElasticsearch().setIndexPrefix("operate-prefix");
    final var tasklistProperties = new TasklistProperties();
    tasklistProperties.getElasticsearch().setIndexPrefix("tasklist-prefix");
    final var configuration =
        new BackupPriorityConfiguration(operateProperties, tasklistProperties);
    assertThatThrownBy(configuration::backupPriorities)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operate-prefix")
        .hasMessageContaining("tasklist-prefix");
  }
}
