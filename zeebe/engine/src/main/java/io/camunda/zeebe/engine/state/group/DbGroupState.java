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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
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
import java.util.function.LongSupplier;

/**
 * Stores every version of a group under the key (groupId, policyVersion). A read returns the latest
 * version that is not newer than the visible version, and a tombstone means no group.
 *
 * <p>The name cache maps a visible version and a name to the IDs of the groups with that name. The
 * cache is derived from state, and every write invalidates it, so it never returns a stale mapping.
 */
public class DbGroupState implements MutableGroupState {

  private final DbString groupId = new DbString();
  private final DbLong policyVersion = new DbLong();
  private final DbCompositeKey<DbString, DbLong> versionedKey =
      new DbCompositeKey<>(groupId, policyVersion);
  private final PersistedGroup persistedGroup = new PersistedGroup();
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, PersistedGroup> groupColumnFamily;
  private final LongSupplier currentVersion;

  private final LoadingCache<NameKey, Set<String>> groupIdsByName;

  public DbGroupState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final EngineConfiguration config,
      final LongSupplier currentVersion) {
    this.currentVersion = currentVersion;
    groupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GROUPS, transactionContext, versionedKey, new PersistedGroup());

    groupIdsByName =
        CacheBuilder.newBuilder()
            .maximumSize(config.getGroupNameCacheCapacity())
            .build(
                new CacheLoader<>() {
                  @Override
                  public Set<String> load(final NameKey key) {
                    // On a miss, scan the groups visible at the version once, and populate every
                    // name of that version so that lookups of other names need no further scan.
                    final Map<String, Set<String>> allEntries =
                        scanAllGroupIdsByName(key.version());
                    allEntries.forEach(
                        (name, ids) -> groupIdsByName.put(new NameKey(key.version(), name), ids));
                    return allEntries.getOrDefault(key.name(), Set.of());
                  }
                });
  }

  @Override
  public void create(final GroupRecord group) {
    write(group, false);
  }

  @Override
  public void update(final GroupRecord group) {
    write(group, false);
  }

  @Override
  public void delete(final GroupRecord group) {
    write(group, true);
  }

  @Override
  public Optional<PersistedGroup> get(final String groupId, final long commandVersion) {
    final long visible = visibleVersion(commandVersion);
    this.groupId.wrapString(groupId);
    policyVersion.wrapLong(visible);
    final var found = new PersistedGroup[1];
    groupColumnFamily.whileTrueReverse(
        versionedKey,
        (key, value) -> {
          if (!value.getGroupId().equals(groupId)) {
            return false;
          }
          if (key.second().getValue() > visible) {
            return true;
          }
          if (!value.isDeleted()) {
            found[0] = copyOf(value);
          }
          return false;
        });
    return Optional.ofNullable(found[0]);
  }

  @Override
  public List<PersistedGroup> findByIdOrName(final String value, final long commandVersion) {
    final var byId = get(value, commandVersion);
    if (byId.isPresent()) {
      return List.of(byId.get());
    }

    final long visible = visibleVersion(commandVersion);
    final List<PersistedGroup> matchingGroups = new ArrayList<>();
    for (final String id : groupIdsByName.getUnchecked(new NameKey(visible, value))) {
      get(id, commandVersion).ifPresent(matchingGroups::add);
    }
    return matchingGroups;
  }

  private void write(final GroupRecord group, final boolean deleted) {
    groupId.wrapString(group.getGroupId());
    policyVersion.wrapLong(group.getPolicyVersion());
    persistedGroup.wrap(group);
    persistedGroup.setDeleted(deleted);
    groupColumnFamily.upsert(versionedKey, persistedGroup);
    groupIdsByName.invalidateAll();
  }

  private long visibleVersion(final long commandVersion) {
    return Math.max(currentVersion.getAsLong(), commandVersion);
  }

  /** Maps each name to the IDs of the visible, non-deleted groups with that name. */
  private Map<String, Set<String>> scanAllGroupIdsByName(final long version) {
    final Map<String, PersistedGroup> visible = new HashMap<>();
    final Map<String, Long> visibleVersions = new HashMap<>();
    groupColumnFamily.whileTrue(
        (key, value) -> {
          final long entryVersion = key.second().getValue();
          if (entryVersion <= version
              && entryVersion > visibleVersions.getOrDefault(value.getGroupId(), -1L)) {
            visibleVersions.put(value.getGroupId(), entryVersion);
            visible.put(value.getGroupId(), copyOf(value));
          }
          return true;
        });

    final Map<String, Set<String>> idsByName = new HashMap<>();
    visible.values().stream()
        .filter(group -> !group.isDeleted())
        .forEach(
            group ->
                idsByName
                    .computeIfAbsent(group.getName(), k -> new LinkedHashSet<>())
                    .add(group.getGroupId()));
    return idsByName;
  }

  private static PersistedGroup copyOf(final PersistedGroup group) {
    final var copy = new PersistedGroup();
    copy.copyFrom(group);
    return copy;
  }

  private record NameKey(long version, String name) {}
}
