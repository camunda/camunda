/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class GroupRecord extends UnifiedRecordValue implements GroupRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue GROUP_KEY_KEY = new StringValue("groupKey");
  private static final StringValue GROUP_ID_KEY = new StringValue("groupId");
  private static final StringValue NAME_KEY = new StringValue("name");
  private static final StringValue DESCRIPTION_KEY = new StringValue("description");
  private static final StringValue ENTITY_ID_KEY = new StringValue("entityId");
  private static final StringValue ENTITY_TYPE_KEY = new StringValue("entityType");
  private static final StringValue ENTITY_IDS_KEY = new StringValue("entityIds");
  private static final StringValue EXTERNAL_ID_KEY = new StringValue("externalId");
  private static final StringValue ATTRIBUTES_KEY = new StringValue("attributes");
  private static final StringValue CHANGED_ATTRIBUTES_KEY = new StringValue("changedAttributes");

  private final LongProperty groupKeyProp = new LongProperty(GROUP_KEY_KEY, -1L);
  private final StringProperty groupId = new StringProperty(GROUP_ID_KEY);
  private final StringProperty nameProp = new StringProperty(NAME_KEY, "");
  private final StringProperty descriptionProp = new StringProperty(DESCRIPTION_KEY, "");
  private final StringProperty entityIdProp = new StringProperty(ENTITY_ID_KEY, "");
  private final EnumProperty<EntityType> entityTypeProp =
      new EnumProperty<>(ENTITY_TYPE_KEY, EntityType.class, EntityType.UNSPECIFIED);
  private final StringProperty externalIdProp = new StringProperty(EXTERNAL_ID_KEY, "");
  private final DocumentProperty attributesProp = new DocumentProperty(ATTRIBUTES_KEY);
  private final ArrayProperty<StringValue> entityIdsProp =
      new ArrayProperty<>(ENTITY_IDS_KEY, StringValue::new);

  /**
   * Command-only field mask. When non-empty, an update command only changes the listed attributes
   * and leaves all others untouched; when empty, the legacy update semantics apply. Events carry it
   * cleared.
   */
  private final ArrayProperty<StringValue> changedAttributesProp =
      new ArrayProperty<>(CHANGED_ATTRIBUTES_KEY, StringValue::new);

  public GroupRecord() {
    super(10);
    declareProperty(groupKeyProp)
        .declareProperty(groupId)
        .declareProperty(nameProp)
        .declareProperty(descriptionProp)
        .declareProperty(entityIdProp)
        .declareProperty(entityTypeProp)
        .declareProperty(externalIdProp)
        .declareProperty(changedAttributesProp)
        .declareProperty(entityIdsProp)
        .declareProperty(attributesProp);
  }

  @Override
  public long getGroupKey() {
    return groupKeyProp.getValue();
  }

  public GroupRecord setGroupKey(final long groupKey) {
    groupKeyProp.setValue(groupKey);
    return this;
  }

  @Override
  public String getGroupId() {
    return BufferUtil.bufferAsString(groupId.getValue());
  }

  public GroupRecord setGroupId(final String groupId) {
    this.groupId.setValue(groupId);
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public GroupRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getDescription() {
    return BufferUtil.bufferAsString(descriptionProp.getValue());
  }

  public GroupRecord setDescription(final String description) {
    if (description == null) {
      descriptionProp.reset();
      return this;
    }

    descriptionProp.setValue(description);
    return this;
  }

  @Override
  public String getEntityId() {
    return BufferUtil.bufferAsString(entityIdProp.getValue());
  }

  public GroupRecord setEntityId(final String entityId) {
    entityIdProp.setValue(entityId);
    return this;
  }

  @Override
  public List<String> getEntityIds() {
    final List<String> entityIds = new ArrayList<>();
    for (final StringValue value : entityIdsProp) {
      entityIds.add(BufferUtil.bufferAsString(value.getValue()));
    }
    return entityIds;
  }

  public GroupRecord setEntityIds(final Collection<String> entityIds) {
    entityIdsProp.reset();
    if (entityIds != null) {
      entityIds.forEach(entityId -> entityIdsProp.add().wrap(BufferUtil.wrapString(entityId)));
    }
    return this;
  }

  @Override
  public EntityType getEntityType() {
    return entityTypeProp.getValue();
  }

  public GroupRecord setEntityType(final EntityType entityType) {
    entityTypeProp.setValue(entityType);
    return this;
  }

  @Override
  public String getExternalId() {
    return BufferUtil.bufferAsString(externalIdProp.getValue());
  }

  public GroupRecord setExternalId(final String externalId) {
    if (externalId == null) {
      return this;
    }
    externalIdProp.setValue(externalId);
    return this;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return MsgPackConverter.convertToMap(attributesProp.getValue());
  }

  public GroupRecord setAttributes(final Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      attributesProp.reset();
    } else {
      attributesProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(attributes)));
    }
    return this;
  }

  public GroupRecord setAttributes(final DirectBuffer attributes) {
    attributesProp.setValue(attributes);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getAttributesBuffer() {
    return attributesProp.getValue();
  }

  /** Command-only; not part of the record value API or its JSON representation. */
  @JsonIgnore
  public Set<GroupAttribute> getChangedAttributes() {
    final Set<GroupAttribute> attributes = EnumSet.noneOf(GroupAttribute.class);
    for (final StringValue value : changedAttributesProp) {
      attributes.add(GroupAttribute.valueOf(BufferUtil.bufferAsString(value.getValue())));
    }
    return attributes;
  }

  public GroupRecord setChangedAttributes(final Collection<GroupAttribute> attributes) {
    changedAttributesProp.reset();
    if (attributes != null) {
      attributes.forEach(
          attribute -> changedAttributesProp.add().wrap(BufferUtil.wrapString(attribute.name())));
    }
    return this;
  }

  public GroupRecord clearChangedAttributes() {
    changedAttributesProp.reset();
    return this;
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getExternalIdBuffer() {
    return externalIdProp.getValue();
  }

  /** Attributes of a group that a masked update command can change. */
  public enum GroupAttribute {
    NAME,
    DESCRIPTION,
    EXTERNAL_ID,
    /** Replaces the stored attributes with the attributes of the command. */
    ATTRIBUTES,
    /**
     * Merges the attributes of the command into the stored ones: a null value removes the key, any
     * other value sets it.
     */
    ATTRIBUTES_MERGE
  }
}
