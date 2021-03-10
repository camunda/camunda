/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.deployment;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.gateway.api.util.StubbedBrokerClient;
import io.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerDeployProcessRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import org.agrona.DirectBuffer;

public final class DeployProcessStub
    implements RequestStub<BrokerDeployProcessRequest, BrokerResponse<DeploymentRecord>> {

  private static final long KEY = 123;
  private static final long PROCESS_KEY = 456;
  private static final int PROCESS_VERSION = 789;
  private static final DirectBuffer CHECKSUM = wrapString("checksum");

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerDeployProcessRequest.class, this);
  }

  protected long getKey() {
    return KEY;
  }

  protected long getProcessDefinitionKey() {
    return PROCESS_KEY;
  }

  public int getProcessVersion() {
    return PROCESS_VERSION;
  }

  @Override
  public BrokerResponse<DeploymentRecord> handle(final BrokerDeployProcessRequest request)
      throws Exception {
    final DeploymentRecord deploymentRecord = request.getRequestWriter();
    deploymentRecord
        .resources()
        .iterator()
        .forEachRemaining(
            r -> {
              deploymentRecord
                  .processes()
                  .add()
                  .setBpmnProcessId(r.getResourceNameBuffer())
                  .setResourceName(r.getResourceNameBuffer())
                  .setVersion(PROCESS_VERSION)
                  .setKey(PROCESS_KEY)
                  .setChecksum(CHECKSUM)
                  .setResource(r.getResourceBuffer());
            });
    return new BrokerResponse<>(deploymentRecord, 0, KEY);
  }
}
