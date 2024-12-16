/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.scaling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.record.value.scaling.RedistributionProgressValue;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "length",
  "empty"
})
public final class RedistributionProgress extends UnpackedObject
    implements RedistributionProgressValue {

  /**
   * The key of the latest deployment that has been redistributed. Deployments with keys less than
   * this value have been redistributed, while deployments with keys greater than this value are not
   * yet redistributed.
   */
  LongProperty deploymentKey = new LongProperty("deploymentKey", -1);

  public RedistributionProgress() {
    super(1);
    declareProperty(deploymentKey);
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey.getValue();
  }

  public RedistributionProgress claimDeploymentKey(final long deploymentKey) {
    if (deploymentKey > this.deploymentKey.getValue()) {
      this.deploymentKey.setValue(deploymentKey);
    }
    return this;
  }
}
