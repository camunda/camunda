/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.Metrics;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class ProcessInstancesByIdArchiverJob extends AbstractProcessInstancesArchiverJob {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstancesByIdArchiverJob.class);

  private final ThreadPoolTaskScheduler executor;

  @Autowired
  public ProcessInstancesByIdArchiverJob(
      final Archiver archiver,
      final List<Integer> partitionIds,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependantTemplates,
      final Metrics metrics,
      final ArchiverRepository archiverRepository,
      @Qualifier("archiverThreadPoolExecutor") final ThreadPoolTaskScheduler executor) {
    super(
        archiver,
        partitionIds,
        processInstanceTemplate,
        processInstanceDependantTemplates,
        metrics,
        archiverRepository,
        LOGGER);
    this.executor = executor;
  }

  @Override
  protected CompletableFuture<Integer> archiveProcessInstances(final ArchiveBatch archiveBatch) {
    final String finishDate = archiveBatch.getFinishDate();
    final List<Object> keys = archiveBatch.getIds();

    final String listViewDest =
        getArchiver()
            .getDestinationIndexName(
                getProcessInstanceTemplate().getFullQualifiedName(), finishDate);

    final Map<String, List<Object>> piKeyFilter =
        Map.of(ListViewTemplate.PROCESS_INSTANCE_KEY, keys);

    // 1. First archive docs from dependent indices and `joinRelation={variable OR activity}`
    // from operate-list-view index
    // 2. Then archive all docs except `joinRelation=processInstance` from operate-list-view index
    // 3. Then archive all `joinRelation=processInstance` docs from operate-list-view index
    return archiveProcessDependants(archiveBatch, listViewDest, piKeyFilter)
        // Phase 2: list-view excluding processInstance join (catch-all for any other joins)
        .thenComposeAsync(
            v ->
                archive(
                    getProcessInstanceTemplate().getFullQualifiedName(),
                    listViewDest,
                    piKeyFilter,
                    Map.of(),
                    Map.of(
                        ListViewTemplate.JOIN_RELATION,
                        ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION)),
            executor)
        // Phase 3: list-view processInstance join only (must be last to avoid dangling data)
        .thenComposeAsync(
            v ->
                archive(
                    getProcessInstanceTemplate().getFullQualifiedName(),
                    listViewDest,
                    piKeyFilter,
                    Map.of(
                        ListViewTemplate.JOIN_RELATION,
                        ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION),
                    Map.of()),
            executor)
        .thenApplyAsync(v -> keys.size(), executor);
  }

  private CompletableFuture<Void> archiveProcessDependants(
      final ArchiveBatch archiveBatch,
      final String listViewDest,
      final Map<String, List<Object>> piKeyFilter) {
    final String finishDate = archiveBatch.getFinishDate();
    final List<Object> keys = archiveBatch.getIds();

    // archive dependent indices and list-view variables + activities in parallel
    final var futures = new ArrayList<CompletableFuture<Void>>();
    for (final ProcessInstanceDependant template : getProcessInstanceDependantTemplates()) {
      futures.add(
          archive(
              template.getFullQualifiedName(),
              getArchiver().getDestinationIndexName(template.getFullQualifiedName(), finishDate),
              Map.of(ProcessInstanceDependant.PROCESS_INSTANCE_KEY, keys),
              Map.of(),
              Map.of()));
    }
    futures.add(
        archive(
            getProcessInstanceTemplate().getFullQualifiedName(),
            listViewDest,
            piKeyFilter,
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.VARIABLES_JOIN_RELATION),
            Map.of()));
    futures.add(
        archive(
            getProcessInstanceTemplate().getFullQualifiedName(),
            listViewDest,
            piKeyFilter,
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.ACTIVITIES_JOIN_RELATION),
            Map.of()));
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  private CompletableFuture<Void> archive(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<Object>> keysByField,
      final Map<String, String> inclusionFilters,
      final Map<String, String> exclusionFilters) {
    return getArchiverRepository()
        .moveDocumentsById(
            sourceIndexName,
            destinationIndexName,
            keysByField,
            inclusionFilters,
            exclusionFilters,
            executor);
  }
}
