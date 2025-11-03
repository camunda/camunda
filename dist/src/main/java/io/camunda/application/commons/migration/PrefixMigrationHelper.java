/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import static io.camunda.webapps.schema.descriptors.ComponentNames.TASK_LIST;

import io.camunda.application.StandalonePrefixMigration.OperateIndexPrefixPropertiesOverride;
import io.camunda.application.StandalonePrefixMigration.TasklistIndexPrefixPropertiesOverride;
import io.camunda.migration.commons.storage.MigrationRepositoryIndex;
import io.camunda.migration.commons.storage.TasklistMigrationRepositoryIndex;
import io.camunda.migration.task.adapter.TaskLegacyIndex;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.PrefixMigrationClient;
import io.camunda.search.schema.elasticsearch.ElasticsearchPrefixMigrationClient;
import io.camunda.search.schema.opensearch.OpensearchPrefixMigrationClient;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.MessageTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public final class PrefixMigrationHelper {
  @VisibleForTesting
  public static final Set<Class<? extends AbstractIndexDescriptor>> TASKLIST_INDICES_TO_MIGRATE =
      Set.of(
          FormIndex.class,
          // Not pointing to the new TaskTemplate as we care about the old version
          TaskLegacyIndex.class,
          DraftTaskVariableTemplate.class,
          TasklistMetricIndex.class,
          SnapshotTaskVariableTemplate.class,
          TasklistMigrationRepositoryIndex.class,
          TasklistImportPositionIndex.class);

  @VisibleForTesting
  public static final Set<Class<? extends AbstractIndexDescriptor>> OPERATE_INDICES_TO_MIGRATE =
      Set.of(
          ListViewTemplate.class,
          JobTemplate.class,
          MetricIndex.class,
          BatchOperationTemplate.class,
          ProcessIndex.class,
          DecisionRequirementsIndex.class,
          DecisionIndex.class,
          EventTemplate.class,
          VariableTemplate.class,
          PostImporterQueueTemplate.class,
          SequenceFlowTemplate.class,
          MessageTemplate.class,
          DecisionInstanceTemplate.class,
          IncidentTemplate.class,
          FlowNodeInstanceTemplate.class,
          OperationTemplate.class,
          ImportPositionIndex.class,
          MigrationRepositoryIndex.class,
          MetadataIndex.class);

  @VisibleForTesting
  public static final Set<Class<? extends AbstractIndexDescriptor>>
      OPERATE_DEPRECATED_INDICES_TO_DELETE =
          Set.of(OperateUserIndex.class, OperateWebSessionIndex.class, OperateUserTaskIndex.class);

  @VisibleForTesting
  public static final Set<Class<? extends AbstractIndexDescriptor>>
      TASKLIST_DEPRECATED_INDICES_TO_DELETE =
          Set.of(
              TasklistUserIndex.class,
              TasklistWebSessionIndex.class,
              TasklistFlownodeInstanceIndex.class,
              TasklistProcessInstanceIndex.class,
              TasklistProcessIndex.class,
              TasklistVariableIndex.class);

  private static final Logger LOG = LoggerFactory.getLogger(PrefixMigrationHelper.class);

  private PrefixMigrationHelper() {}

  public static void runPrefixMigration(
      final OperateIndexPrefixPropertiesOverride operateProperties,
      final TasklistIndexPrefixPropertiesOverride tasklistProperties,
      final ConnectConfiguration connectConfiguration) {
    final var isElasticsearch = connectConfiguration.getTypeEnum() == DatabaseType.ELASTICSEARCH;

    final var operatePrefix =
        isElasticsearch
            ? operateProperties.elasticsearchIndexPrefix()
            : operateProperties.opensearchIndexPrefix();
    final var tasklistPrefix =
        isElasticsearch
            ? tasklistProperties.elasticsearchIndexPrefix()
            : tasklistProperties.opensearchIndexPrefix();

    final var prefixMigrationClient = getPrefixMigrationClient(connectConfiguration);
    final var targetPrefix = connectConfiguration.getIndexPrefix();
    final var descriptors = new IndexDescriptors(targetPrefix, isElasticsearch);
    final var executor = Executors.newVirtualThreadPerTaskExecutor();

    final var migrateFutures =
        PrefixMigrationHelper.migrate(
            operatePrefix,
            tasklistPrefix,
            targetPrefix,
            isElasticsearch,
            descriptors,
            prefixMigrationClient,
            executor);

    final var deleteFutures =
        PrefixMigrationHelper.deleteDeprecatedIndices(
            operatePrefix, tasklistPrefix, isElasticsearch, prefixMigrationClient, descriptors);

    final var deleteIndexTemplateFutures =
        PrefixMigrationHelper.deleteIndexTemplates(
            operatePrefix,
            tasklistPrefix,
            isElasticsearch,
            prefixMigrationClient,
            descriptors,
            executor);

    final var deleteComponentTemplateFutures =
        PrefixMigrationHelper.deleteComponentTemplates(
            operatePrefix, tasklistPrefix, prefixMigrationClient);

    CompletableFuture.allOf(migrateFutures.get())
        .thenRun(() -> LOG.info("Migration of indices completed."))
        .thenCompose(v -> CompletableFuture.allOf(deleteFutures.get()))
        .thenRun(() -> LOG.info("Deletion of indices completed."))
        .thenCompose(v -> CompletableFuture.allOf(deleteIndexTemplateFutures.get()))
        .thenRun(() -> LOG.info("Deletion of index templates completed."))
        .thenCompose(v -> CompletableFuture.allOf(deleteComponentTemplateFutures.get()))
        .thenRun(() -> LOG.info("Deletion of component templates completed."))
        .join();

    executor.close();
  }

  public static Supplier<CompletableFuture[]> migrate(
      final String operatePrefix,
      final String tasklistPrefix,
      final String targetPrefix,
      final boolean isElasticsearch,
      final IndexDescriptors descriptors,
      final PrefixMigrationClient prefixMigrationClient,
      final ExecutorService executor) {

    final var cloneOperations = new ArrayList<AliasCloneTargets>();

    if (StringUtils.hasText(tasklistPrefix)) {
      TASKLIST_INDICES_TO_MIGRATE.forEach(
          cls ->
              buildCloneTargets(
                      tasklistPrefix,
                      targetPrefix,
                      isElasticsearch,
                      prefixMigrationClient,
                      cls,
                      descriptors)
                  .map(cloneOperations::add));
    }

    if (StringUtils.hasText(operatePrefix)) {
      OPERATE_INDICES_TO_MIGRATE.forEach(
          cls ->
              buildCloneTargets(
                      operatePrefix,
                      targetPrefix,
                      isElasticsearch,
                      prefixMigrationClient,
                      cls,
                      descriptors)
                  .map(cloneOperations::add));
    }

    return () ->
        cloneOperations.stream()
            .parallel()
            .map(
                entry ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          final var innerFutures =
                              entry.indices().stream()
                                  .parallel()
                                  .map(
                                      idx ->
                                          prefixMigrationClient.cloneAndDeleteIndex(
                                              idx.getLeft(),
                                              entry.srcAlias,
                                              idx.getRight(),
                                              entry.destAlias))
                                  .toArray(CompletableFuture[]::new);
                          return CompletableFuture.allOf(innerFutures).join();
                        },
                        executor))
            .toArray(CompletableFuture[]::new);
  }

  private static Supplier<CompletableFuture> deleteDeprecatedIndices(
      final String operatePrefix,
      final String tasklistPrefix,
      final boolean isElasticsearch,
      final PrefixMigrationClient prefixMigrationClient,
      final IndexDescriptors descriptors) {

    final var deleteOperations = new ArrayList<String>();

    if (StringUtils.hasText(operatePrefix)) {
      OPERATE_DEPRECATED_INDICES_TO_DELETE.stream()
          .flatMap(
              cls ->
                  indicesToDelete(
                      operatePrefix, isElasticsearch, prefixMigrationClient, cls, descriptors)
                      .stream())
          .forEach(deleteOperations::add);
    }

    if (StringUtils.hasText(tasklistPrefix)) {
      TASKLIST_DEPRECATED_INDICES_TO_DELETE.stream()
          .flatMap(
              cls ->
                  indicesToDelete(
                      tasklistPrefix, isElasticsearch, prefixMigrationClient, cls, descriptors)
                      .stream())
          .forEach(deleteOperations::add);
    }

    return () ->
        deleteOperations.isEmpty()
            ? CompletableFuture.completedFuture(null)
            : CompletableFuture.supplyAsync(
                () ->
                    prefixMigrationClient
                        .deleteIndex(deleteOperations.toArray(String[]::new))
                        .join());
  }

  private static Supplier<CompletableFuture[]> deleteIndexTemplates(
      final String operatePrefix,
      final String tasklistPrefix,
      final boolean isElasticsearch,
      final PrefixMigrationClient prefixMigrationClient,
      final IndexDescriptors descriptors,
      final ExecutorService executor) {

    final String indexTemplateFormat = "%s-%s-%s_template";
    final var targetTemplates = new ArrayList<AbstractIndexDescriptor>();

    if (StringUtils.hasText(operatePrefix)) {
      OPERATE_INDICES_TO_MIGRATE.stream()
          .map(cls -> getDescriptor(cls, descriptors, operatePrefix, isElasticsearch))
          .filter(AbstractTemplateDescriptor.class::isInstance)
          .forEach(targetTemplates::add);

      OPERATE_DEPRECATED_INDICES_TO_DELETE.stream()
          .map(cls -> getDescriptor(cls, descriptors, operatePrefix, isElasticsearch))
          .filter(AbstractTemplateDescriptor.class::isInstance)
          .forEach(targetTemplates::add);
    }

    if (StringUtils.hasText(tasklistPrefix)) {
      TASKLIST_INDICES_TO_MIGRATE.stream()
          .map(cls -> getDescriptor(cls, descriptors, tasklistPrefix, isElasticsearch))
          .filter(AbstractTemplateDescriptor.class::isInstance)
          .forEach(targetTemplates::add);

      TASKLIST_DEPRECATED_INDICES_TO_DELETE.stream()
          .map(cls -> getDescriptor(cls, descriptors, tasklistPrefix, isElasticsearch))
          .filter(AbstractTemplateDescriptor.class::isInstance)
          .forEach(targetTemplates::add);
    }

    return () ->
        targetTemplates.stream()
            .map(
                desc ->
                    String.format(
                        indexTemplateFormat,
                        desc.getComponentName().equals(TASK_LIST.toString())
                            ? tasklistPrefix
                            : operatePrefix,
                        desc.getIndexName(),
                        desc.getVersion()))
            .map(
                indexTemplate ->
                    CompletableFuture.supplyAsync(
                            () -> prefixMigrationClient.deleteIndexTemplate(indexTemplate),
                            executor)
                        .join())
            .toArray(CompletableFuture[]::new);
  }

  private static Supplier<CompletableFuture[]> deleteComponentTemplates(
      final String operatePrefix,
      final String tasklistPrefix,
      final PrefixMigrationClient prefixMigrationClient) {

    final String operateComponentTemplate = "%s_template".formatted(operatePrefix);
    final String tasklistComponentTemplate = "%s_template".formatted(tasklistPrefix);

    return () -> {
      final var futures = new ArrayList<CompletableFuture<?>>();
      if (StringUtils.hasText(operatePrefix)) {
        futures.add(
            CompletableFuture.supplyAsync(
                () -> prefixMigrationClient.deleteComponentTemplate(operateComponentTemplate)));
      }
      if (StringUtils.hasText(tasklistPrefix)) {
        futures.add(
            CompletableFuture.supplyAsync(
                () -> prefixMigrationClient.deleteComponentTemplate(tasklistComponentTemplate)));
      }
      return futures.toArray(CompletableFuture[]::new);
    };
  }

  private static Optional<AliasCloneTargets> buildCloneTargets(
      final String oldPrefix,
      final String newPrefix,
      final boolean isElasticsearch,
      final PrefixMigrationClient prefixMigrationClient,
      final Class<? extends AbstractIndexDescriptor> cls,
      final IndexDescriptors descriptors) {
    final var descriptor = getDescriptor(cls, descriptors, newPrefix, isElasticsearch);
    final var srcAlias = getSourceAliasFromDescriptor(oldPrefix, descriptor);
    final var destAlias = getDestinationAliasFromDescriptor(descriptor);
    final var srcIndices = prefixMigrationClient.getIndicesInAlias(srcAlias);

    final var targetIndices =
        srcIndices.stream()
            .map(
                srcIndex -> {
                  final String destIndex;
                  if (isHistoricIndex(
                      srcIndex, descriptor.getIndexName(), descriptor.getVersion())) {
                    destIndex =
                        srcIndex.replace(
                            oldPrefix,
                            descriptor.getIndexPrefix() + "-" + descriptor.getComponentName());
                  } else {
                    destIndex = descriptor.getFullQualifiedName();
                  }
                  return Tuple.of(srcIndex, destIndex);
                })
            .toList();

    if (!targetIndices.isEmpty()) {
      return Optional.of(new AliasCloneTargets(srcAlias, destAlias, targetIndices));
    }
    return Optional.empty();
  }

  private static List<String> indicesToDelete(
      final String oldPrefix,
      final boolean isElasticsearch,
      final PrefixMigrationClient prefixMigrationClient,
      final Class<? extends AbstractIndexDescriptor> cls,
      final IndexDescriptors descriptors) {
    final var descriptor = getDescriptor(cls, descriptors, oldPrefix, isElasticsearch);
    final var srcAlias = getSourceAliasFromDescriptor(oldPrefix, descriptor);
    return prefixMigrationClient.getIndicesInAlias(srcAlias);
  }

  public static boolean isHistoricIndex(
      final String indexName, final String properIndexName, final String version) {
    return !indexName.endsWith("-%s-%s_".formatted(properIndexName, version));
  }

  private static String getSourceAliasFromDescriptor(
      final String oldPrefix, final AbstractIndexDescriptor descriptor) {
    final String aliasFormat = "%s-%s-%s_alias";
    return String.format(
        aliasFormat, oldPrefix, descriptor.getIndexName(), descriptor.getVersion());
  }

  private static String getDestinationAliasFromDescriptor(
      final AbstractIndexDescriptor descriptor) {
    return descriptor.getAlias();
  }

  private static PrefixMigrationClient getPrefixMigrationClient(
      final ConnectConfiguration connectConfiguration) {
    if (connectConfiguration.getTypeEnum().isElasticSearch()) {
      return new ElasticsearchPrefixMigrationClient(
          new ElasticsearchConnector(connectConfiguration).createClient());
    } else {
      return new OpensearchPrefixMigrationClient(
          new OpensearchConnector(connectConfiguration).createClient());
    }
  }

  @VisibleForTesting
  public static AbstractIndexDescriptor getDescriptor(
      final Class<? extends AbstractIndexDescriptor> cls,
      final IndexDescriptors descriptors,
      final String prefix,
      final boolean isElasticsearch) {
    if (cls.equals(TaskLegacyIndex.class)) {
      return new TaskLegacyIndex(prefix, isElasticsearch);
    } else if (cls.equals(TasklistMigrationRepositoryIndex.class)) {
      return new TasklistMigrationRepositoryIndex(prefix, isElasticsearch);
    } else if (cls.equals(MigrationRepositoryIndex.class)) {
      return new MigrationRepositoryIndex(prefix, isElasticsearch);
    } else if (cls.equals(TasklistUserIndex.class)) {
      return new TasklistUserIndex(prefix, isElasticsearch);
    } else if (cls.equals(TasklistWebSessionIndex.class)) {
      return new TasklistWebSessionIndex(prefix, isElasticsearch);
    } else if (cls.equals(TasklistFlownodeInstanceIndex.class)) {
      return new TasklistFlownodeInstanceIndex(prefix, isElasticsearch);
    } else if (cls.equals(TasklistProcessInstanceIndex.class)) {
      return new TasklistProcessInstanceIndex(prefix, isElasticsearch);
    } else if (cls.equals(TasklistProcessIndex.class)) {
      return new TasklistProcessIndex(prefix, isElasticsearch);
    } else if (cls.equals(TasklistVariableIndex.class)) {
      return new TasklistVariableIndex(prefix, isElasticsearch);
    } else if (cls.equals(OperateUserIndex.class)) {
      return new OperateUserIndex(prefix, isElasticsearch);
    } else if (cls.equals(OperateWebSessionIndex.class)) {
      return new OperateWebSessionIndex(prefix, isElasticsearch);
    } else if (cls.equals(OperateUserTaskIndex.class)) {
      return new OperateUserTaskIndex(prefix, isElasticsearch);
    }
    return descriptors.get(cls);
  }

  private record AliasCloneTargets(
      String srcAlias, String destAlias, List<Tuple<String, String>> indices) {}

  private static class TasklistUserIndex extends AbstractIndexDescriptor {

    public TasklistUserIndex(final String indexPrefix, final boolean isElasticsearch) {
      super(indexPrefix, isElasticsearch);
    }

    @Override
    public String getIndexName() {
      return "user";
    }

    @Override
    public String getVersion() {
      return "1.4.0";
    }

    @Override
    public String getComponentName() {
      return "tasklist";
    }
  }

  private static class TasklistWebSessionIndex extends AbstractIndexDescriptor {

    public TasklistWebSessionIndex(final String indexPrefix, final boolean isElasticsearch) {
      super(indexPrefix, isElasticsearch);
    }

    @Override
    public String getIndexName() {
      return "web-session";
    }

    @Override
    public String getVersion() {
      return "1.1.0";
    }

    @Override
    public String getComponentName() {
      return "tasklist";
    }
  }

  private static class TasklistFlownodeInstanceIndex extends AbstractIndexDescriptor {

    public TasklistFlownodeInstanceIndex(final String indexPrefix, final boolean isElasticsearch) {
      super(indexPrefix, isElasticsearch);
    }

    @Override
    public String getIndexName() {
      return "flownode-instance";
    }

    @Override
    public String getVersion() {
      return "8.3.0";
    }

    @Override
    public String getComponentName() {
      return "tasklist";
    }
  }

  private static class TasklistProcessInstanceIndex extends AbstractIndexDescriptor {

    public TasklistProcessInstanceIndex(final String indexPrefix, final boolean isElasticsearch) {
      super(indexPrefix, isElasticsearch);
    }

    @Override
    public String getIndexName() {
      return "process-instance";
    }

    @Override
    public String getVersion() {
      return "8.3.0";
    }

    @Override
    public String getComponentName() {
      return "tasklist";
    }
  }

  private static class TasklistProcessIndex extends AbstractIndexDescriptor {

    public TasklistProcessIndex(final String indexPrefix, final boolean isElasticsearch) {
      super(indexPrefix, isElasticsearch);
    }

    @Override
    public String getIndexName() {
      return "process";
    }

    @Override
    public String getVersion() {
      return "8.4.0";
    }

    @Override
    public String getComponentName() {
      return "tasklist";
    }
  }

  private static class TasklistVariableIndex extends AbstractIndexDescriptor {

    public TasklistVariableIndex(final String indexPrefix, final boolean isElasticsearch) {
      super(indexPrefix, isElasticsearch);
    }

    @Override
    public String getIndexName() {
      return "variable";
    }

    @Override
    public String getVersion() {
      return "8.3.0";
    }

    @Override
    public String getComponentName() {
      return "tasklist";
    }
  }

  private static class OperateUserIndex extends AbstractIndexDescriptor {

    public OperateUserIndex(final String indexPrefix, final boolean isElasticsearch) {
      super(indexPrefix, isElasticsearch);
    }

    @Override
    public String getIndexName() {
      return "user";
    }

    @Override
    public String getVersion() {
      return "1.2.0";
    }

    @Override
    public String getComponentName() {
      return "operate";
    }
  }

  private static class OperateWebSessionIndex extends AbstractIndexDescriptor {

    public OperateWebSessionIndex(final String indexPrefix, final boolean isElasticsearch) {
      super(indexPrefix, isElasticsearch);
    }

    @Override
    public String getIndexName() {
      return "web-session";
    }

    @Override
    public String getVersion() {
      return "1.1.0";
    }

    @Override
    public String getComponentName() {
      return "operate";
    }
  }

  private static class OperateUserTaskIndex extends AbstractTemplateDescriptor {

    public OperateUserTaskIndex(final String indexPrefix, final boolean isElasticsearch) {
      super(indexPrefix, isElasticsearch);
    }

    @Override
    public String getIndexName() {
      return "user-task";
    }

    @Override
    public String getVersion() {
      return "8.5.0";
    }

    @Override
    public String getComponentName() {
      return "operate";
    }
  }
}
