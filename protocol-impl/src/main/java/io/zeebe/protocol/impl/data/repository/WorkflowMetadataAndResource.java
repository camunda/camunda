/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.data.repository;

import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public final class WorkflowMetadataAndResource extends WorkflowMetadata {
  private final StringProperty bpmnXmlProp = new StringProperty("bpmnXml");

  public WorkflowMetadataAndResource() {
    super();
    declareProperty(bpmnXmlProp);
  }

  public DirectBuffer getBpmnXml() {
    return bpmnXmlProp.getValue();
  }

  public WorkflowMetadataAndResource setBpmnXml(final String bpmnXml) {
    bpmnXmlProp.setValue(bpmnXml);
    return this;
  }

  public WorkflowMetadataAndResource setBpmnXml(final DirectBuffer val) {
    bpmnXmlProp.setValue(val);
    return this;
  }
}
