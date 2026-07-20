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
import static org.mockito.Mockito.mock;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.webapps.schema.descriptors.backup.BackupPriority;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class BackupPrioritiesTest {

  static final List<JavaClass> ALL_IMPLEMENTATIONS;

  static {
    ALL_IMPLEMENTATIONS =
        new ClassFileImporter()
                .importPackages("io.camunda.webapps.schema")
                .that(implement(BackupPriority.class))
                .stream()
                .filter(clz -> !clz.getName().contains("Abstract"))
                .toList();
  }

  @BeforeAll
  public static void setUp() {
    new UnifiedConfigurationHelper(mock(Environment.class));
  }

  @Test
  public void allImplementationsContainIndicesFromAllApps() {
    final var index =
        ALL_IMPLEMENTATIONS.stream()
            .filter(clz -> clz.getPackage().getName().contains("index"))
            .toList();
    assertThat(index).isNotEmpty();
    final var template =
        ALL_IMPLEMENTATIONS.stream()
            .filter(clz -> clz.getPackage().getName().contains("template"))
            .toList();
    assertThat(template).isNotEmpty();
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

  @Test
  public void testBackupPriorities() {
    final var configuration = new BackupPriorityConfiguration(new Camunda());
    final var priorities = configuration.backupPriorities();

    final Set<String> allPriorities =
        priorities.allPriorities().map(obj -> obj.getClass().getName()).collect(Collectors.toSet());

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
    final var configuration = new BackupPriorityConfiguration(new Camunda());
    final var priorities = configuration.backupPriorities();

    final var indices = priorities.indicesSplitBySnapshot().toList();

    assertThat(indices.size()).isEqualTo(7);
    final var iterator = indices.iterator();
    // PRIO 1
    assertThat(iterator.next().allIndices())
        .containsExactlyInAnyOrder(
            "operate-metadata-8.8.0_",
            "camunda-history-deletion-8.9.0_",
            "operate-process-8.3.0_",
            "operate-decision-8.3.0_",
            "operate-decision-requirements-8.3.0_",
            "tasklist-form-8.4.0_");
    // PRIO 2 main indices
    assertThat(iterator.next().allIndices())
        .containsExactlyInAnyOrder("operate-list-view-8.3.0_", "tasklist-task-8.8.0_");
    // PRIO 2 dated indices
    assertThat(iterator.next().allIndices())
        .containsExactlyInAnyOrder(
            "operate-list-view-8.3.0_*",
            "-operate-list-view-8.3.0_",
            "tasklist-task-8.8.0_*",
            "-tasklist-task-8.8.0_");
    // PRIO 3 main indices
    assertThat(iterator.next().allIndices())
        .containsExactlyInAnyOrder(
            "camunda-agent-instance-8.10.0_",
            "camunda-correlated-message-subscription-8.8.0_",
            "operate-batch-operation-1.0.0_",
            "operate-operation-8.4.1_",
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
    // PRIO 3 dated indices
    assertThat(iterator.next().allIndices())
        .containsExactlyInAnyOrder(
            "camunda-agent-instance-8.10.0_*",
            "-camunda-agent-instance-8.10.0_",
            "camunda-correlated-message-subscription-8.8.0_*",
            "-camunda-correlated-message-subscription-8.8.0_",
            "operate-batch-operation-1.0.0_*",
            "-operate-batch-operation-1.0.0_",
            "operate-operation-8.4.1_*",
            "-operate-operation-8.4.1_",
            "operate-decision-instance-8.3.0_*",
            "-operate-decision-instance-8.3.0_",
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
            "tasklist-draft-task-variable-8.3.0_*",
            "-tasklist-draft-task-variable-8.3.0_",
            "tasklist-task-variable-8.3.0_*",
            "-tasklist-task-variable-8.3.0_");

    // PRIO 4 main indices
    assertThat(iterator.next().allIndices())
        .containsExactlyInAnyOrder(
            "camunda-authorization-8.8.0_",
            "camunda-group-8.8.0_",
            "camunda-mapping-rule-8.8.0_",
            "camunda-web-session-8.8.0_",
            "camunda-role-8.8.0_",
            "camunda-tenant-8.8.0_",
            "camunda-user-8.8.0_",
            "camunda-usage-metric-8.8.0_",
            "camunda-usage-metric-tu-8.8.0_",
            "camunda-agent-history-8.10.0_",
            "camunda-audit-log-8.9.0_",
            "camunda-audit-log-cleanup-8.9.0_",
            "camunda-cluster-variable-8.9.0_",
            "camunda-job-metrics-batch-8.9.0_",
            "camunda-global-listener-8.9.0_",
            "camunda-deployed-resource-8.10.0_",
            "camunda-wait-state-8.10.0_");

    // PRIO 4 dated indices
    assertThat(iterator.next().allIndices())
        .containsExactlyInAnyOrder(
            "camunda-usage-metric-8.8.0_*",
            "-camunda-usage-metric-8.8.0_",
            "camunda-usage-metric-tu-8.8.0_*",
            "-camunda-usage-metric-tu-8.8.0_",
            "camunda-agent-history-8.10.0_*",
            "-camunda-agent-history-8.10.0_",
            "camunda-audit-log-8.9.0_*",
            "-camunda-audit-log-8.9.0_",
            "camunda-job-metrics-batch-8.9.0_*",
            "-camunda-job-metrics-batch-8.9.0_",
            "camunda-wait-state-8.10.0_*",
            "-camunda-wait-state-8.10.0_");

    for (final var indexList : indices) {
      assertThat(indexList.allIndices())
          .allSatisfy(i -> assertThat(i).doesNotStartWith("optimize"));
    }
  }

  @Test
  public void parentEntitiesAreBackedUpBeforeDependents() {
    final var configuration = new BackupPriorityConfiguration(new Camunda());
    final var priorities = configuration.backupPriorities();

    final var tierByClass = new HashMap<Class<?>, Integer>();
    recordTier(priorities.prio1(), 1, tierByClass);
    recordTier(priorities.prio2(), 2, tierByClass);
    recordTier(priorities.prio3(), 3, tierByClass);
    recordTier(priorities.prio4(), 4, tierByClass);

    assertThat(tierByClass.get(ProcessIndex.class))
        .describedAs(
            "ProcessIndex (process definition) must be backed up before "
                + "ListViewTemplate (process instances), its dependents")
        .isLessThan(tierByClass.get(ListViewTemplate.class));
    assertThat(tierByClass.get(ProcessIndex.class))
        .describedAs(
            "ProcessIndex (process definition) must be backed up before "
                + "MessageSubscriptionTemplate, which references it")
        .isLessThan(tierByClass.get(MessageSubscriptionTemplate.class));
    assertThat(tierByClass.get(DecisionIndex.class))
        .describedAs(
            "DecisionIndex must be backed up before DecisionInstanceTemplate, " + "its dependent")
        .isLessThan(tierByClass.get(DecisionInstanceTemplate.class));
    assertThat(tierByClass.get(FormIndex.class))
        .describedAs("FormIndex must be backed up before TaskTemplate, which references it")
        .isLessThan(tierByClass.get(TaskTemplate.class));
  }

  private static void recordTier(
      final List<? extends BackupPriority> tierMembers,
      final int tier,
      final Map<Class<?>, Integer> tierByClass) {
    tierMembers.forEach(member -> tierByClass.put(member.getClass(), tier));
  }
}
