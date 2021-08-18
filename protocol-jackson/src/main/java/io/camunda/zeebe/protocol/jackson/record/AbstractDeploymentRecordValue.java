/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.zeebe.protocol.jackson.record.DeploymentRecordValueBuilder.ImmutableDeploymentRecordValue;
import io.camunda.zeebe.protocol.jackson.record.DeploymentResourceBuilder.ImmutableDeploymentResource;
import io.camunda.zeebe.protocol.jackson.record.ProcessMetadataValueBuilder.ImmutableProcessMetadataValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@ZeebeStyle
@JsonDeserialize(as = ImmutableDeploymentRecordValue.class)
public abstract class AbstractDeploymentRecordValue
    implements DeploymentRecordValue, DefaultJsonSerializable {
  @JsonDeserialize(contentAs = ImmutableDeploymentResource.class)
  @Override
  public abstract List<DeploymentResource> getResources();

  @JsonDeserialize(contentAs = ImmutableProcessMetadataValue.class)
  @Override
  public abstract List<ProcessMetadataValue> getProcessesMetadata();
}
