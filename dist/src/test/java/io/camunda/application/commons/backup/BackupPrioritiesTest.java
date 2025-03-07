/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.implement;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CAMUNDA_OPTIMIZE_DATABASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.camunda.application.commons.backup.BackupPriorityConfiguration.OptimizePrio1Delegate;
import io.camunda.application.commons.backup.BackupPriorityConfiguration.OptimizePrio6Delegate;
import io.camunda.db.DatabaseType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
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
import org.springframework.core.env.Environment;

class BackupPrioritiesTest {

  static final List<JavaClass> ALL_IMPLEMENTATIONS;

  static final Set<String> OPTIMIZE_INDICES_TO_IGNORE =
      Set.of(
          "io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES",
          "io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES");

  static {
    ALL_IMPLEMENTATIONS =
        new ClassFileImporter()
            .importPackages(
                "io.camunda.webapps.schema",
                // Just scan the ES instances of the classes
                "io.camunda.optimize.service.db.es.schema.index")
            .that(implement(BackupPriority.class))
            .stream()
            .filter(clz -> !clz.getName().contains("Abstract"))
            .filter(clz -> !OPTIMIZE_INDICES_TO_IGNORE.contains(clz.getName()))
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
    final var optimize =
        ALL_IMPLEMENTATIONS.stream()
            .filter(clz -> clz.getPackage().getName().contains("optimize"))
            .toList();
    assertThat(optimize).isNotEmpty();
  }

  @Test
  public void onlyOneInterfaceIsImplemented() {
    assertThat(ALL_IMPLEMENTATIONS)
        .allSatisfy(
            clazz ->
                assertThat(
                    clazz.getAllRawInterfaces().stream()
                        .filter(i -> i.getName().matches(".*.Prio\\d+Backup"))
                        .count())
                    .isEqualTo(1));
  }

  public static Stream<Object[]> properties() {
    return Stream.of(null, new OperateProperties())
        .flatMap(
            op ->
                Stream.of(null, new TasklistProperties())
                    .map(tasklist -> new Object[]{op, tasklist}))
        // At least one property must be configured
        .filter(arr -> !Arrays.stream(arr).allMatch(Objects::isNull));
  }

  @Test
  public void testBackupPriorities() {
    final var configuration =
        new BackupPriorityConfiguration(
            new OperateProperties(),
            new TasklistProperties(),
            new OptimizeIndexNameService(""),
            matchingProfiles("operate", "tasklist", "optimize"));
    final var priorities = configuration.backupPriorities();

    final Set<String> allPriorities =
        priorities
            .allPriorities()
            .map(
                obj -> {
                  if (obj instanceof final OptimizePrio6Delegate<?> p) {
                    return p.index().getClass().getName();
                  }
                  if (obj instanceof final OptimizePrio1Delegate<?> p) {
                    return p.index().getClass().getName();
                  }
                  return obj.getClass().getName();
                })
            .collect(Collectors.toSet());

    final var missingInBackup = new HashSet<String>();
    ALL_IMPLEMENTATIONS.forEach(
        clzz -> {
          final var noSubclassMatch =
              clzz.getSubclasses().stream().noneMatch(sub -> allPriorities.contains(sub.getName()));
          if (!allPriorities.contains(clzz.getName()) && noSubclassMatch) {
            missingInBackup.add(clzz.getName());
          }
        });
    assertThat(missingInBackup).isEmpty();
  }

  @Test
  public void testBackupPrioritiesIndicesSplitBySnapshot() {
    final var configuration =
        new BackupPriorityConfiguration(
            new OperateProperties(), null, null, matchingProfiles("operate"));
    final var priorities = configuration.backupPriorities();

    final var indices = priorities.indicesSplitBySnapshot().toList();

    assertThat(indices.size()).isEqualTo(8);
    // PRIO 1
    assertThat(indices.get(0).allIndices())
        .containsExactlyInAnyOrder(
            "operate-import-position-8.3.0_",
            "tasklist-import-position-8.2.0_",
            "optimize-position-based-import-index_v3",
            "optimize-timestamp-based-import-index_v5");
    // PRIO 2
    assertThat(indices.get(1).allIndices())
        .containsExactlyInAnyOrder("operate-list-view-8.3.0_", "tasklist-task-8.5.0_");
    // PRIO 2 TEMPLATES
    assertThat(indices.get(2).allIndices())
        .containsExactlyInAnyOrder(
            "operate-list-view-8.3.0_*",
            "-operate-list-view-8.3.0_",
            "tasklist-task-8.5.0_*",
            "-tasklist-task-8.5.0_");
    // PRIO 3
    assertThat(indices.get(3).allIndices())
        .containsExactlyInAnyOrder("operate-batch-operation-1.0.0_", "operate-operation-8.4.1_");
    // PRIO 4
    assertThat(indices.get(4).allIndices())
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
            "operate-variable-8.3.0_",
            "tasklist-draft-task-variable-8.3.0_",
            "tasklist-task-variable-8.3.0_");

    // PRIO 4 TEMPLATES
    assertThat(indices.get(5).allIndices())
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
            "operate-variable-8.3.0_*",
            "-operate-variable-8.3.0_",
            "-operate-decision-instance-8.3.0_",
            "operate-decision-instance-8.3.0_*",
            "-tasklist-draft-task-variable-8.3.0_",
            "tasklist-draft-task-variable-8.3.0_*",
            "-tasklist-task-variable-8.3.0_",
            "tasklist-task-variable-8.3.0_*");

    // PRIO 5
    assertThat(indices.get(6).allIndices())
        .containsExactlyInAnyOrder(
            "operate-decision-requirements-8.3.0_",
            "operate-metric-8.3.0_",
            "operate-process-8.3.0_",
            "tasklist-form-8.4.0_",
            "tasklist-metric-8.3.0_",
            "camunda-authorization-8.8.0_",
            "camunda-group-8.8.0_",
            "camunda-mapping-8.8.0_",
            "camunda-web-session-8.8.0_",
            "camunda-role-8.8.0_",
            "camunda-tenant-8.8.0_",
            "camunda-user-8.8.0_");

    // PRIO6
    assertThat(indices.get(7).allIndices())
        .containsExactlyInAnyOrder(
            "optimize-single-decision-report_v10",
            "optimize-process-overview_v2",
            "optimize-instant-dashboard_v1",
            "optimize-business-key_v2",
            "optimize-single-process-report_v11",
            "optimize-combined-report_v5",
            "optimize-dashboard_v8",
            "optimize-dashboard-share_v4",
            "optimize-variable-update-instance_v2-000001",
            "optimize-variable-label_v1",
            "optimize-terminated-user-session_v3",
            "optimize-settings_v3",
            "optimize-collection_v5",
            "optimize-report-share_v3",
            "optimize-decision-definition_v5",
            "optimize-tenant_v3",
            "optimize-metadata_v3",
            "optimize-external-process-variable_v2-000001",
            "optimize-process-definition_v6",
            "optimize-alert_v4");

    for (final var indexList : indices) {
      assertThat(indexList.requiredIndices())
          .allSatisfy(i -> assertThat(i).doesNotStartWith("optimize"));
    }
  }

  @Test
  public void shouldFailIfIndexPrefixIsDifferent() {
    final var operateProperties = new OperateProperties();
    operateProperties.getElasticsearch().setIndexPrefix("operate-prefix");
    final var tasklistProperties = new TasklistProperties();
    tasklistProperties.getElasticsearch().setIndexPrefix("tasklist-prefix");
    final var optimizeIndexService = new OptimizeIndexNameService("optimize-prefix");
    final var configuration =
        new BackupPriorityConfiguration(
            operateProperties,
            tasklistProperties,
            optimizeIndexService,
            matchingProfiles("operate", "tasklist", "optimize"));
    assertThatThrownBy(configuration::backupPriorities)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operate-prefix")
        .hasMessageContaining("tasklist-prefix");
  }

  public Environment matchingProfiles(final String... profiles) {
    final var environment = mock(Environment.class);
    final var profileSet = new HashSet<>(Arrays.asList(profiles));
    when(environment.matchesProfiles(any()))
        .thenAnswer(
            arg -> {
              final var profileArg = (String) arg.getArgument(0);
              return profileSet.contains(profileArg);
            });
    when(environment.getActiveProfiles()).thenReturn(profiles);
    when(environment.getProperty(eq(CAMUNDA_OPTIMIZE_DATABASE), (String) any()))
        .thenReturn(DatabaseType.ELASTICSEARCH.toString());
    return environment;
  }
}
