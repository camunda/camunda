/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.deployment;

import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedProcessMetadataValue;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

public final class DeploymentRecord extends UnifiedRecordValue implements DeploymentRecordValue {

  public static final String RESOURCES = "resources";
  public static final String PROCESSES = "deployedProcesses";

  private final ArrayProperty<DeploymentResource> resourcesProp =
      new ArrayProperty<>(RESOURCES, new DeploymentResource());

  private final ArrayProperty<DeployedProcessMetadata> processesMetadataProp =
      new ArrayProperty<>(PROCESSES, new DeployedProcessMetadata());

  public DeploymentRecord() {
    declareProperty(resourcesProp).declareProperty(processesMetadataProp);
  }

  public ValueArray<DeployedProcessMetadata> processesMetadata() {
    return processesMetadataProp;
  }

  public ValueArray<DeploymentResource> resources() {
    return resourcesProp;
  }

  @Override
  public List<io.zeebe.protocol.record.value.deployment.DeploymentResource> getResources() {
    final List<io.zeebe.protocol.record.value.deployment.DeploymentResource> resources =
        new ArrayList<>();

    for (final DeploymentResource resource : resourcesProp) {
      final byte[] bytes = new byte[resource.getLength()];
      final UnsafeBuffer copyBuffer = new UnsafeBuffer(bytes);
      resource.write(copyBuffer, 0);

      final DeploymentResource copiedResource = new DeploymentResource();
      copiedResource.wrap(copyBuffer);
      resources.add(copiedResource);
    }

    return resources;
  }

  @Override
  public List<DeployedProcessMetadataValue> getProcessesMetadata() {
    final List<DeployedProcessMetadataValue> processesMeta = new ArrayList<>();

    for (final DeployedProcessMetadata processRecord : processesMetadataProp) {
      final byte[] bytes = new byte[processRecord.getLength()];
      final UnsafeBuffer copyBuffer = new UnsafeBuffer(bytes);
      processRecord.write(copyBuffer, 0);

      final DeployedProcessMetadata copiedProcessRecord = new DeployedProcessMetadata();
      copiedProcessRecord.wrap(copyBuffer);
      processesMeta.add(copiedProcessRecord);
    }

    return processesMeta;
  }
}
