/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * Stores every version of a membership in both directions. A membership is keyed by its entity and
 * relation plus the policy version, and its value says whether the membership is active or was
 * removed at that version. A read takes the latest version that is visible to the command, so a
 * removal of a membership hides it, exactly as a tombstone does for the other identity objects.
 */
public final class DbMembershipState implements MutableMembershipState {

  private final EntityKeyAndRelationKey entityKeyAndRelationKey = new EntityKeyAndRelationKey();
  private final RelationKeyAndEntityKey relationKeyAndEntityKey = new RelationKeyAndEntityKey();
  private final VersionedEntityKey versionedEntityKey = new VersionedEntityKey();
  private final VersionedRelationKey versionedRelationKey = new VersionedRelationKey();
  private final DbMembershipStatus status = new DbMembershipStatus();

  private final ColumnFamily<VersionedEntityKey, DbMembershipStatus> relationsByEntity;
  private final ColumnFamily<VersionedRelationKey, DbMembershipStatus> entitiesByRelation;
  private final LongSupplier currentVersion;

  public DbMembershipState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final LongSupplier currentVersion) {
    this.currentVersion = currentVersion;
    relationsByEntity =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RELATIONS_BY_ENTITY,
            transactionContext,
            versionedEntityKey,
            new DbMembershipStatus());

    entitiesByRelation =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITIES_BY_RELATION,
            transactionContext,
            versionedRelationKey,
            new DbMembershipStatus());
  }

  @Override
  public void insertRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId,
      final long policyVersion) {
    write(entityType, entityId, relationType, relationId, policyVersion, MembershipStatus.ACTIVE);
  }

  @Override
  public void deleteRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId,
      final long policyVersion) {
    write(entityType, entityId, relationType, relationId, policyVersion, MembershipStatus.REMOVED);
  }

  @Override
  public List<String> getMemberships(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final long commandVersion) {
    final long visible = visibleVersion(commandVersion);
    final Map<String, Object[]> latest = new LinkedHashMap<>();
    relationsByEntity.whileEqualPrefix(
        new EntityKeyAndRelationType(entityType, entityId, relationType),
        (key, value) -> {
          final long version = key.second().getValue();
          if (version <= visible) {
            // entries of one relation are contiguous and ordered by version, so the last one wins
            latest.put(key.first().second().id(), new Object[] {version, value.getValue()});
          }
        });

    final var relationIds = new ArrayList<String>();
    latest.forEach(
        (relationId, entry) -> {
          if (entry[1] == MembershipStatus.ACTIVE) {
            relationIds.add(relationId);
          }
        });
    return relationIds;
  }

  @Override
  public void forEachMember(
      final RelationType relationType,
      final String relationId,
      final long commandVersion,
      final BiFunction<EntityType, String, Boolean> visitor) {
    final long visible = visibleVersion(commandVersion);
    final Map<String, Object[]> latest = new LinkedHashMap<>();
    entitiesByRelation.whileEqualPrefix(
        new RelationKey(relationType, relationId),
        (key, value) -> {
          final long version = key.second().getValue();
          if (version <= visible) {
            final var entity = key.first().entity();
            latest.put(
                entity.type() + "/" + entity.id(),
                new Object[] {entity.type(), entity.id(), value.getValue()});
          }
        });

    for (final Object[] entry : latest.values()) {
      if (entry[2] == MembershipStatus.ACTIVE
          && !visitor.apply((EntityType) entry[0], (String) entry[1])) {
        return;
      }
    }
  }

  @Override
  public boolean hasRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId,
      final long commandVersion) {
    final long visible = visibleVersion(commandVersion);
    versionedEntityKey.first().setAll(entityType, entityId, relationType, relationId);
    versionedEntityKey.second().wrapLong(visible);
    final var active = new boolean[1];
    relationsByEntity.whileTrueReverse(
        versionedEntityKey,
        (key, value) -> {
          final var pair = key.first();
          if (!pair.first().type().equals(entityType)
              || !pair.first().id().equals(entityId)
              || !pair.relation().type().equals(relationType)
              || !pair.relation().id().equals(relationId)) {
            return false;
          }
          if (key.second().getValue() > visible) {
            return true;
          }
          active[0] = value.getValue() == MembershipStatus.ACTIVE;
          return false;
        });
    return active[0];
  }

  private void write(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId,
      final long version,
      final MembershipStatus membershipStatus) {
    versionedEntityKey.first().setAll(entityType, entityId, relationType, relationId);
    versionedEntityKey.second().wrapLong(version);
    status.setValue(membershipStatus);
    relationsByEntity.upsert(versionedEntityKey, status);

    versionedRelationKey.first().setAll(entityType, entityId, relationType, relationId);
    versionedRelationKey.second().wrapLong(version);
    entitiesByRelation.upsert(versionedRelationKey, status);
  }

  private long visibleVersion(final long commandVersion) {
    return Math.max(currentVersion.getAsLong(), commandVersion);
  }

  private static final class EntityKey extends DbCompositeKey<DbEnumValue<EntityType>, DbString> {
    public EntityKey() {
      super(new DbEnumValue<>(EntityType.class), new DbString());
    }

    public void setAll(final EntityType entityType, final String entityId) {
      first().setValue(entityType);
      second().wrapString(entityId);
    }

    public String id() {
      return second().toString();
    }

    public EntityType type() {
      return first().getValue();
    }
  }

  private static final class RelationKey
      extends DbCompositeKey<DbEnumValue<RelationType>, DbString> {
    public RelationKey() {
      super(new DbEnumValue<>(RelationType.class), new DbString());
    }

    public RelationKey(final RelationType relationType, final String relationId) {
      super(new DbEnumValue<>(RelationType.class), new DbString());
      setAll(relationType, relationId);
    }

    void setAll(final RelationType relationType, final String relationId) {
      first().setValue(relationType);
      second().wrapString(relationId);
    }

    public String id() {
      return second().toString();
    }

    public RelationType type() {
      return first().getValue();
    }
  }

  private static final class EntityKeyAndRelationKey
      extends DbCompositeKey<EntityKey, RelationKey> {
    public EntityKeyAndRelationKey() {
      super(new EntityKey(), new RelationKey());
    }

    public void setAll(
        final EntityType entityType,
        final String entityId,
        final RelationType relationType,
        final String relationId) {
      first().setAll(entityType, entityId);
      second().setAll(relationType, relationId);
    }

    public RelationKey relation() {
      return second();
    }
  }

  private static final class EntityKeyAndRelationType
      extends DbCompositeKey<EntityKey, DbEnumValue<RelationType>> {
    public EntityKeyAndRelationType(
        final EntityType entityType, final String entityId, final RelationType relationType) {
      super(new EntityKey(), new DbEnumValue<>(RelationType.class));
      first().setAll(entityType, entityId);
      second().setValue(relationType);
    }
  }

  private static final class RelationKeyAndEntityKey
      extends DbCompositeKey<RelationKey, EntityKey> {
    public RelationKeyAndEntityKey() {
      super(new RelationKey(), new EntityKey());
    }

    public void setAll(
        final EntityType entityType,
        final String entityId,
        final RelationType relationType,
        final String relationId) {
      first().setAll(relationType, relationId);
      second().setAll(entityType, entityId);
    }

    public EntityKey entity() {
      return second();
    }
  }

  /** The relation key of a membership together with the policy version of the entry. */
  private static final class VersionedEntityKey
      extends DbCompositeKey<EntityKeyAndRelationKey, DbLong> {
    public VersionedEntityKey() {
      super(new EntityKeyAndRelationKey(), new DbLong());
    }
  }

  /** The entity-side key of a membership together with the policy version of the entry. */
  private static final class VersionedRelationKey
      extends DbCompositeKey<RelationKeyAndEntityKey, DbLong> {
    public VersionedRelationKey() {
      super(new RelationKeyAndEntityKey(), new DbLong());
    }
  }

  /** Whether a membership is present or was removed at its policy version. */
  public enum MembershipStatus {
    ACTIVE,
    REMOVED,
  }

  public enum RelationType {
    ROLE,
    GROUP,
    TENANT,
  }
}
