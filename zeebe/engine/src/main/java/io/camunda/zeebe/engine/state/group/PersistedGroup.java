/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.group;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;

public class PersistedGroup extends UnpackedObject
    implements DbValue, ProjectionOf<GroupRecord, PersistedGroup> {

  private final LongProperty groupKeyProp = new LongProperty("groupKey");
  private final StringProperty groupIdProp = new StringProperty("groupId");
  private final StringProperty descriptionProp = new StringProperty("description");
  private final StringProperty nameProp = new StringProperty("name");
  private final StringProperty externalIdProp = new StringProperty("externalId", "");
  private final DocumentProperty attributesProp = new DocumentProperty("attributes");

  public PersistedGroup() {
    super(6);
    declareProperty(groupKeyProp)
        .declareProperty(groupIdProp)
        .declareProperty(descriptionProp)
        .declareProperty(nameProp)
        .declareProperty(externalIdProp)
        .declareProperty(attributesProp);
  }

  @Override
  public PersistedGroup wrap(final GroupRecord group) {
    groupKeyProp.setValue(group.getGroupKey());
    groupIdProp.setValue(group.getGroupId());
    descriptionProp.setValue(group.getDescription());
    nameProp.setValue(group.getNameBuffer());
    externalIdProp.setValue(group.getExternalIdBuffer());
    attributesProp.setValue(group.getAttributesBuffer());
    return this;
  }

  public long getGroupKey() {
    return groupKeyProp.getValue();
  }

  public PersistedGroup setGroupKey(final long groupKey) {
    groupKeyProp.setValue(groupKey);
    return this;
  }

  public String getGroupId() {
    return BufferUtil.bufferAsString(groupIdProp.getValue());
  }

  public PersistedGroup setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public String getDescription() {
    return BufferUtil.bufferAsString(descriptionProp.getValue());
  }

  public PersistedGroup setDescription(final String description) {
    descriptionProp.setValue(description);
    return this;
  }

  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public PersistedGroup setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public String getExternalId() {
    return BufferUtil.bufferAsString(externalIdProp.getValue());
  }

  public PersistedGroup setExternalId(final String externalId) {
    externalIdProp.setValue(externalId);
    return this;
  }

  public Map<String, Object> getAttributes() {
    return MsgPackConverter.convertToMap(attributesProp.getValue());
  }

  public PersistedGroup setAttributes(final Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      attributesProp.reset();
    } else {
      attributesProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(attributes)));
    }
    return this;
  }
}
