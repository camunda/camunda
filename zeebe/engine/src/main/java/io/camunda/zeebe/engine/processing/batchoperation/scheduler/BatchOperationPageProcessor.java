/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import com.google.common.collect.Lists;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.Item;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.ItemPage;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationInitializationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationItem;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class processes pages of items for batch operations, creating and appending chunk records to
 * the task result builder. It handles the logic for chunking items and ensuring that the task
 * result can accommodate the new records.
 */
public class BatchOperationPageProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationPageProcessor.class);
  // These empty records are used only for size checking in canAppendChunks,
  // to ensure that the size check accounts for the largest possible record types
  // that may be appended. They are not used for actual data processing.
  private static final UnifiedRecordValue EMPTY_EXECUTION_RECORD =
      new BatchOperationExecutionRecord().setBatchOperationKey(-1L);
  private static final UnifiedRecordValue EMPTY_INITIALIZATION_RECORD =
      new BatchOperationInitializationRecord()
          .setBatchOperationKey(-1L)
          .setSearchQueryPageSize(0)
          .setSearchResultCursor(RandomStringUtils.insecure().next(1024));

  private final int chunkSize;

  public BatchOperationPageProcessor(final int chunkSize) {
    this.chunkSize = chunkSize;
  }

  /**
   * Processes a page of items, appending them as chunks to the task result builder.
   *
   * <p>This method takes a page of items, processes them into chunks of a specified size, and
   * appends the chunks to the provided task result builder. It returns a result indicating whether
   * chunks were appended, the end cursor of the page, the number of items processed, and whether it
   * was the last page of items.
   *
   * @param batchOperationKey the key of the batch operation being processed
   * @param page the page of items to process
   * @param taskResultBuilder the builder to append command records to
   * @return a result indicating whether chunks were appended, the end cursor, number of items
   *     processed, and if it was the last page
   */
  public PageProcessingResult processPage(
      final long batchOperationKey,
      final ItemPage page,
      final TaskResultBuilder taskResultBuilder) {
    final boolean appendedChunks = appendChunks(taskResultBuilder, batchOperationKey, page.items());
    return new PageProcessingResult(
        appendedChunks, page.endCursor(), page.items().size(), page.isLastPage());
  }

  private boolean appendChunks(
      final TaskResultBuilder taskResultBuilder,
      final long batchOperationKey,
      final List<Item> items) {

    final var chunkRecords = createChunks(batchOperationKey, items);
    final FollowUpCommandMetadata metadata =
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey));

    if (canAppendChunks(taskResultBuilder, chunkRecords, metadata)) {
      chunkRecords.forEach(
          command -> {
            LOG.trace(
                "Appending batch operation {} chunk with {} items.",
                batchOperationKey,
                command.getItems().size());
            taskResultBuilder.appendCommandRecord(
                batchOperationKey, BatchOperationChunkIntent.CREATE, command, metadata);
          });
      return true;
    }
    return false;
  }

  private List<BatchOperationChunkRecord> createChunks(
      final long batchOperationKey, final List<Item> items) {
    return Lists.partition(items, chunkSize).stream()
        .map(chunkItems -> createChunkRecord(batchOperationKey, chunkItems))
        .toList();
  }

  private boolean canAppendChunks(
      final TaskResultBuilder taskResultBuilder,
      final List<BatchOperationChunkRecord> chunkRecords,
      final FollowUpCommandMetadata metadata) {
    final List<UnifiedRecordValue> sizeCheckRecords = new ArrayList<>(chunkRecords);
    sizeCheckRecords.add(EMPTY_EXECUTION_RECORD);
    sizeCheckRecords.add(EMPTY_INITIALIZATION_RECORD);
    return taskResultBuilder.canAppendRecords(sizeCheckRecords, metadata);
  }

  private static BatchOperationChunkRecord createChunkRecord(
      final long batchOperationKey, final List<Item> chunkItems) {
    final var command = new BatchOperationChunkRecord();
    command.setBatchOperationKey(batchOperationKey);
    command.setItems(
        chunkItems.stream().map(BatchOperationPageProcessor::mapItem).collect(Collectors.toSet()));
    return command;
  }

  private static BatchOperationItem mapItem(final Item item) {
    return new BatchOperationItem()
        .setItemKey(item.itemKey())
        .setProcessInstanceKey(item.processInstanceKey())
        .setRootProcessInstanceKey(Optional.ofNullable(item.rootProcessInstanceKey()).orElse(-1L));
  }

  public record PageProcessingResult(
      boolean chunksAppended, String endCursor, int itemsProcessed, boolean isLastPage) {}
}
