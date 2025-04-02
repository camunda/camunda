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
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class DbMembershipState implements MutableMembershipState {
  private final EntityKeyAndRelationKey entityKeyAndRelationKey = new EntityKeyAndRelationKey();
  private final RelationKeyAndEntityKey relationKeyAndEntityKey = new RelationKeyAndEntityKey();

  private final ColumnFamily<EntityKeyAndRelationKey, DbNil> relationsByEntity;
  private final ColumnFamily<RelationKeyAndEntityKey, DbNil> entitiesByRelation;

  public DbMembershipState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    relationsByEntity =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RELATIONS_BY_ENTITY,
            transactionContext,
            entityKeyAndRelationKey,
            DbNil.INSTANCE);

    entitiesByRelation =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITIES_BY_RELATION,
            transactionContext,
            relationKeyAndEntityKey,
            DbNil.INSTANCE);
  }

  @Override
  public void insertRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    entityKeyAndRelationKey.setAll(entityType, entityId, relationType, relationId);
    relationsByEntity.insert(entityKeyAndRelationKey, DbNil.INSTANCE);

    relationKeyAndEntityKey.setAll(entityType, entityId, relationType, relationId);
    entitiesByRelation.insert(relationKeyAndEntityKey, DbNil.INSTANCE);
  }

  @Override
  public void deleteRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    entityKeyAndRelationKey.setAll(entityType, entityId, relationType, relationId);
    relationsByEntity.deleteExisting(entityKeyAndRelationKey);

    relationKeyAndEntityKey.setAll(entityType, entityId, relationType, relationId);
    entitiesByRelation.deleteExisting(relationKeyAndEntityKey);
  }

  @Override
  public List<String> getMemberships(
      final EntityType entityType, final String entityId, final RelationType relationType) {
    final var relationIds = new ArrayList<String>();

    relationsByEntity.whileEqualPrefix(
        new EntityKeyAndRelationType(entityType, entityId, relationType),
        (key, value) -> {
          relationIds.add(key.relation().id());
        });

    return relationIds;
  }

  @Override
  public void forEachMember(
      final RelationType relationType,
      final String relationId,
      final BiConsumer<EntityType, String> visitor) {
    entitiesByRelation.whileEqualPrefix(
        new RelationKey(relationType, relationId),
        (key, value) -> {
          visitor.accept(key.entity().type(), key.entity().id());
        });
  }

  @Override
  public boolean hasRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    entityKeyAndRelationKey.setAll(entityType, entityId, relationType, relationId);
    return relationsByEntity.exists(entityKeyAndRelationKey);
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

  public enum RelationType {
    ROLE,
  }
}
