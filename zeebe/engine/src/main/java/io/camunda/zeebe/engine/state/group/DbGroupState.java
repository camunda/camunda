/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.group;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DbGroupState implements MutableGroupState {

  private final DbString groupId;
  private final PersistedGroup persistedGroup = new PersistedGroup();
  private final ColumnFamily<DbString, PersistedGroup> groupColumnFamily;

  /**
   * Caches group name → list of group IDs sharing that name. The value is always a pure function of
   * persisted state: the loader scans the groups column family on a miss, and every mutation that
   * can change the mapping invalidates the affected name(s). This keeps the cache consistent across
   * broker restarts (the loader repopulates from state) and avoids the in-memory-only staleness
   * that an incrementally maintained map would suffer.
   */
  private final LoadingCache<String, Set<String>> groupIdsByName;

  public DbGroupState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final EngineConfiguration config) {

    groupId = new DbString();
    groupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GROUPS, transactionContext, groupId, new PersistedGroup());

    groupIdsByName =
        CacheBuilder.newBuilder()
            .maximumSize(config.getGroupNameCacheCapacity())
            .build(
                new CacheLoader<>() {
                  @Override
                  public Set<String> load(final String name) {
                    // On a miss, scan all groups once and populate every name so subsequent lookups
                    // of other names are served from cache without another scan.
                    final Map<String, Set<String>> allEntries = scanAllGroupIdsByName();
                    groupIdsByName.putAll(allEntries);
                    return allEntries.getOrDefault(name, Set.of());
                  }
                });
  }

  @Override
  public void create(final GroupRecord group) {
    groupId.wrapString(group.getGroupId());
    persistedGroup.wrap(group);
    groupColumnFamily.insert(groupId, persistedGroup);

    groupIdsByName.invalidate(group.getName());
  }

  @Override
  public void update(final GroupRecord group) {
    groupId.wrapString(group.getGroupId());
    final var persistedGroup = groupColumnFamily.get(groupId);
    if (persistedGroup == null) {
      return;
    }

    final String oldName = persistedGroup.getName();
    persistedGroup.wrap(group);
    groupColumnFamily.update(groupId, persistedGroup);

    groupIdsByName.invalidate(oldName);
    groupIdsByName.invalidate(group.getName());
  }

  @Override
  public void delete(final String groupId) {
    this.groupId.wrapString(groupId);

    final var existingGroup = groupColumnFamily.get(this.groupId);
    if (existingGroup != null) {
      groupIdsByName.invalidate(existingGroup.getName());
    }

    groupColumnFamily.deleteExisting(this.groupId);
  }

  @Override
  public Optional<PersistedGroup> get(final String groupId) {
    this.groupId.wrapString(groupId);
    final var persistedGroup = groupColumnFamily.get(this.groupId, PersistedGroup::new);
    return Optional.ofNullable(persistedGroup);
  }

  @Override
  public List<PersistedGroup> findByIdOrName(final String value) {
    final var byId = get(value);
    if (byId.isPresent()) {
      return List.of(byId.get());
    }

    final List<PersistedGroup> matchingGroups = new ArrayList<>();
    for (final String groupId : groupIdsByName.getUnchecked(value)) {
      get(groupId).ifPresent(matchingGroups::add);
    }
    return matchingGroups;
  }

  private Map<String, Set<String>> scanAllGroupIdsByName() {
    final Map<String, Set<String>> idsByName = new HashMap<>();
    groupColumnFamily.forEach(
        group ->
            idsByName
                .computeIfAbsent(group.getName(), k -> new LinkedHashSet<>())
                .add(group.getGroupId()));
    return idsByName;
  }
}
