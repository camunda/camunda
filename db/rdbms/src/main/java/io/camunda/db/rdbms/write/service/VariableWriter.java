/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionVariableNameLookupDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.InsertVariableMerger;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.QueueItemMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public class VariableWriter extends ProcessInstanceDependant implements RdbmsWriter {

  // the variable insert and update have the default order 0
  private static final int DELETE_PROTECTION_MODES_ORDER = 1;
  private static final int INSERT_PROTECTION_MODES_ORDER = 2;
  private static final String DELETE_PROTECTION_MODES_STATEMENT =
      "io.camunda.db.rdbms.sql.VariableMapper.deleteProtectionModes";
  private static final String INSERT_PROTECTION_MODES_STATEMENT =
      "io.camunda.db.rdbms.sql.VariableMapper.insertProtectionModes";

  private final ExecutionQueue executionQueue;
  private final VariableMapper variableMapper;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final RdbmsWriterConfig config;
  private final Map<Long, Set<String>> processVariableNameCache = new HashMap<>();

  public VariableWriter(
      final ExecutionQueue executionQueue,
      final VariableMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final RdbmsWriterConfig config) {
    super(mapper);
    this.executionQueue = executionQueue;
    variableMapper = mapper;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.config = config;
  }

  public void create(final VariableDbModel variable) {
    final var truncatedVariable =
        variable.truncateValue(
            vendorDatabaseProperties.variableValuePreviewSize(),
            vendorDatabaseProperties.charColumnMaxBytes());

    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new InsertVariableMerger(
                truncatedVariable, config.insertBatchingConfig().variableInsertBatchSize()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.VARIABLE,
              WriteStatementType.INSERT,
              truncatedVariable.variableKey(),
              "io.camunda.db.rdbms.sql.VariableMapper.insert",
              new BatchInsertDto<>(truncatedVariable)));
    }

    maybeRecordVariableNameInLookup(variable);
    insertProtectionModes(truncatedVariable);
  }

  public void update(final VariableDbModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.UPDATE,
            variable.variableKey(),
            "io.camunda.db.rdbms.sql.VariableMapper.update",
            variable.truncateValue(
                vendorDatabaseProperties.variableValuePreviewSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));

    replaceProtectionModes(variable);
  }

  public void migrateToProcess(final long variableKey, final String processDefinitionId) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.UPDATE,
            variableKey,
            "io.camunda.db.rdbms.sql.VariableMapper.migrateToProcess",
            new VariableMapper.MigrateToProcessDto.Builder()
                .variableKey(variableKey)
                .processDefinitionId(processDefinitionId)));
  }

  private void maybeRecordVariableNameInLookup(final VariableDbModel variable) {
    final Long pdKey = variable.processDefinitionKey();
    if (pdKey == null || pdKey <= 0) {
      return;
    }
    final Set<String> names = processVariableNameCache.computeIfAbsent(pdKey, k -> new HashSet<>());
    if (names.add(variable.name())) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.PROCESS_DEF_VAR_NAME_LOOKUP,
              WriteStatementType.INSERT,
              pdKey,
              "io.camunda.db.rdbms.sql.VariableMapper.insertLookupIfNotExists",
              new ProcessDefinitionVariableNameLookupDbModel(pdKey, variable.name())));
    }
  }

  /**
   * Queues the protection modes of a created variable as one insert. Nothing is queued for a
   * variable without protection modes.
   *
   * <p>The insert shares the INSERT type with the batched variable insert, which has the default
   * order. It uses a higher order, so it is executed after the variable insert and satisfies the
   * foreign key from the modes to the variable.
   *
   * <p>A variable has at most one modes insert queued per flush. The variable insert merger drops a
   * re-enqueued CREATED event for a variable already in the batch, so a second modes insert would
   * collide with the first on the primary key. It adds its modes to the queued insert instead.
   */
  private void insertProtectionModes(final VariableDbModel variable) {
    final var variableKey = variable.variableKey();
    final var protectionModes = variable.protectionModes();
    if (protectionModes == null || protectionModes.isEmpty()) {
      return;
    }

    final var modes = new VariableMapper.ProtectionModesDto(variableKey, protectionModes);
    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            modesInsertMerger(
                WriteStatementType.INSERT,
                variableKey,
                queued -> queued.copy(b -> b.parameter(withModesOf(queued, modes)))));

    if (!wasMerged) {
      executionQueue.executeInQueue(modesInsertItem(WriteStatementType.INSERT, modes));
    }
  }

  /**
   * Replaces the protection modes of an updated variable: deletes the existing rows, then inserts
   * the current set.
   *
   * <p>The queue sorts the {@link ContextType#VARIABLE} context by statement type, then order, not
   * by arrival. Two updates of one variable in the same flush would therefore run both deletes
   * before both inserts, so the inserts collide on the primary key and a mode removed by the later
   * update comes back. To prevent this, a variable has at most one modes insert queued per flush. A
   * later update replaces its modes in place. When they become empty, the item turns into a second
   * delete, which does nothing after the first.
   */
  private void replaceProtectionModes(final VariableDbModel variable) {
    final var variableKey = variable.variableKey();
    final var protectionModes = variable.protectionModes();

    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.UPDATE,
            variableKey,
            DELETE_PROTECTION_MODES_STATEMENT,
            variableKey,
            DELETE_PROTECTION_MODES_ORDER));

    final var modes = new VariableMapper.ProtectionModesDto(variableKey, protectionModes);
    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            modesInsertMerger(
                WriteStatementType.UPDATE,
                variableKey,
                queued ->
                    protectionModes.isEmpty()
                        ? queued.copy(
                            b ->
                                b.statementId(DELETE_PROTECTION_MODES_STATEMENT)
                                    .parameter(variableKey))
                        : queued.copy(
                            b ->
                                b.statementId(INSERT_PROTECTION_MODES_STATEMENT)
                                    .parameter(modes))));

    if (!wasMerged && !protectionModes.isEmpty()) {
      executionQueue.executeInQueue(modesInsertItem(WriteStatementType.UPDATE, modes));
    }
  }

  /**
   * The queued modes plus the new ones, so a repeated mode is inserted once and others are kept.
   */
  private static VariableMapper.ProtectionModesDto withModesOf(
      final QueueItem queued, final VariableMapper.ProtectionModesDto modes) {
    final var union =
        new HashSet<>(((VariableMapper.ProtectionModesDto) queued.parameter()).modes());
    union.addAll(modes.modes());
    return new VariableMapper.ProtectionModesDto(modes.variableKey(), union);
  }

  private static QueueItem modesInsertItem(
      final WriteStatementType statementType, final VariableMapper.ProtectionModesDto modes) {
    return new QueueItem(
        ContextType.VARIABLE,
        statementType,
        modes.variableKey(),
        INSERT_PROTECTION_MODES_STATEMENT,
        modes,
        INSERT_PROTECTION_MODES_ORDER);
  }

  /** Matches the queued modes item of the variable and rewrites it with {@code merge}. */
  private static QueueItemMerger modesInsertMerger(
      final WriteStatementType statementType,
      final Long variableKey,
      final UnaryOperator<QueueItem> merge) {
    return new QueueItemMerger() {
      @Override
      public boolean canBeMerged(final QueueItem queueItem) {
        return queueItem.contextType() == ContextType.VARIABLE
            && queueItem.statementType() == statementType
            && queueItem.order() == INSERT_PROTECTION_MODES_ORDER
            && queueItem.id().equals(variableKey);
      }

      @Override
      public QueueItem merge(final QueueItem originalItem) {
        return merge.apply(originalItem);
      }
    };
  }

  public void deleteLookupByProcessDefinitionKeys(
      final Collection<Long> processDefinitionKeys, final int limit) {
    variableMapper.deleteLookupByProcessDefinitionKeys(List.copyOf(processDefinitionKeys), limit);
    processVariableNameCache.keySet().removeAll(processDefinitionKeys);
  }
}
