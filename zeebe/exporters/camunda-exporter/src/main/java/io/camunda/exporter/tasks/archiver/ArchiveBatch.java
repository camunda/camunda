/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Represents a batch of documents to be archived. This interface serves as a common contract for
 * different types of archive batches, such as {@link ProcessInstanceArchiveBatch} for process
 * instances or {@link BasicArchiveBatch} for other entities.
 *
 * <p>Each batch contains specific identifiers for the documents to be archived and a finish date
 * used for determining the destination index.
 */
public interface ArchiveBatch {

  String finishDate();

  int size();

  List<Long> processInstanceKeys();

  List<Long> rootProcessInstanceKeys();

  default boolean isEmpty() {
    return size() == 0;
  }

  record ProcessInstanceArchiveBatch(
      String finishDate, List<Long> processInstanceKeys, List<Long> rootProcessInstanceKeys)
      implements ArchiveBatch {

    @Override
    public int size() {
      return processInstanceKeys.size() + rootProcessInstanceKeys.size();
    }
  }

  record BasicArchiveBatch(String finishDate, List<String> ids) implements ArchiveBatch {

    @Override
    public int size() {
      return ids.size();
    }

    @Override
    public List<Long> processInstanceKeys() {
      return ids.stream().map(Long::parseLong).toList();
    }

    @Override
    public List<Long> rootProcessInstanceKeys() {
      return List.of();
    }
  }

  record ProcessInstanceBatchSizes(
      Map<Long, Long> docCountByProcessInstanceKey,
      Map<Long, Long> docCountByRootProcessInstanceKey) {
    List<ProcessInstanceArchiveBatch> splitBatch(
        final ProcessInstanceArchiveBatch batch, final long maxDocumentsPerBatch) {
      final List<Long> processInstanceKeys = new ArrayList<>(batch.processInstanceKeys());
      final List<Long> rootProcessInstanceKeys = new ArrayList<>(batch.rootProcessInstanceKeys());
      final List<ProcessInstanceArchiveBatch> batches = new ArrayList<>();

      final CurrentBatch currentBatch =
          new CurrentBatch(
              batch.finishDate(), new ArrayList<>(), new ArrayList<>(), new AtomicLong(0L));
      splitBatchByKeys(
          currentBatch,
          processInstanceKeys,
          docCountByProcessInstanceKey,
          currentBatch::addProcessInstanceKey,
          maxDocumentsPerBatch,
          batches);
      splitBatchByKeys(
          currentBatch,
          rootProcessInstanceKeys,
          docCountByRootProcessInstanceKey,
          currentBatch::addRootProcessInstanceKey,
          maxDocumentsPerBatch,
          batches);

      if (!currentBatch.isEmpty()) {
        batches.add(currentBatch.newBatch());
      }

      return batches;
    }

    private void splitBatchByKeys(
        final CurrentBatch currentBatch,
        final List<Long> keys,
        final Map<Long, Long> docCountByKey,
        final BiConsumer<Long, Long> addKeyFunction,
        final long maxDocumentsPerBatch,
        final List<ProcessInstanceArchiveBatch> batches) {
      final Iterator<Long> it = keys.iterator();
      while (it.hasNext()) {
        final Long processInstanceKey = it.next();
        final long docCount = docCountByKey.getOrDefault(processInstanceKey, 0L);
        final long currentBatchDocs = currentBatch.currentDocCount();
        if (!currentBatch.isEmpty() && (currentBatchDocs + docCount) > maxDocumentsPerBatch) {
          batches.add(currentBatch.newBatch());
        }
        addKeyFunction.accept(processInstanceKey, docCount);
        it.remove();
      }
    }

    private record CurrentBatch(
        String finishDate,
        List<Long> processInstanceKeys,
        List<Long> rootProcessInstanceKeys,
        AtomicLong docCount) {

      boolean isEmpty() {
        return processInstanceKeys.isEmpty() && rootProcessInstanceKeys().isEmpty();
      }

      long currentDocCount() {
        return docCount.get();
      }

      void addProcessInstanceKey(final long key, final long count) {
        processInstanceKeys.add(key);
        docCount.addAndGet(count);
      }

      void addRootProcessInstanceKey(final long key, final long count) {
        rootProcessInstanceKeys.add(key);
        docCount.addAndGet(count);
      }

      ProcessInstanceArchiveBatch newBatch() {
        docCount.set(0L);
        return new ProcessInstanceArchiveBatch(
            finishDate, consumeKeys(processInstanceKeys), consumeKeys(rootProcessInstanceKeys));
      }

      private List<Long> consumeKeys(final List<Long> keys) {
        final List<Long> consumedKeys = new ArrayList<>(keys);
        keys.clear();
        return consumedKeys;
      }
    }
  }
}
