/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class DeployResourceStub
    implements RequestStub<BrokerDeployResourceRequest, BrokerResponse<DeploymentRecord>> {

  private static final long KEY = 123;
  private static final long PROCESS_KEY = 234;
  private static final int PROCESS_VERSION = 345;
  private static final String FORM_ID = "test-form-id";
  private static final int FORM_VERSION = 12;
  private static final long FORM_KEY = 11115647343L;
  private static final DirectBuffer CHECKSUM = wrapString("checksum");

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerDeployResourceRequest.class, this);
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
  public BrokerResponse<DeploymentRecord> handle(final BrokerDeployResourceRequest request)
      throws Exception {
    final DeploymentRecord deploymentRecord = request.getRequestWriter();
    final String tenantId = deploymentRecord.getTenantId();
    deploymentRecord
        .resources()
        .iterator()
        .forEachRemaining(
            r -> {
              if (r.getResourceName().endsWith(".bpmn")) {
                deploymentRecord
                    .processesMetadata()
                    .add()
                    .setBpmnProcessId(r.getResourceNameBuffer())
                    .setResourceName(r.getResourceNameBuffer())
                    .setVersion(PROCESS_VERSION)
                    .setKey(PROCESS_KEY)
                    .setChecksum(CHECKSUM)
                    .setTenantId(tenantId);
              } else if (r.getResourceName().endsWith(".dmn")) {
                deploymentRecord
                    .decisionsMetadata()
                    .add()
                    .setDecisionId(r.getResourceName())
                    .setDecisionName(r.getResourceName())
                    .setVersion(456)
                    .setDecisionKey(567)
                    .setDecisionRequirementsId(r.getResourceName())
                    .setDecisionRequirementsKey(678)
                    .setTenantId(tenantId);
                deploymentRecord
                    .decisionRequirementsMetadata()
                    .add()
                    .setDecisionRequirementsId(r.getResourceName())
                    .setDecisionRequirementsName(r.getResourceName())
                    .setDecisionRequirementsVersion(456)
                    .setDecisionRequirementsKey(678)
                    .setNamespace(r.getResourceName())
                    .setResourceName(r.getResourceName())
                    .setChecksum(BufferUtil.wrapString("checksum"))
                    .setTenantId(tenantId);
              } else if (r.getResourceName().endsWith(".form")) {
                deploymentRecord
                    .formMetadata()
                    .add()
                    .setFormId(FORM_ID)
                    .setVersion(FORM_VERSION)
                    .setFormKey(FORM_KEY)
                    .setResourceName(r.getResourceName())
                    .setChecksum(CHECKSUM)
                    .setTenantId(tenantId);
              }
            });
    return new BrokerResponse<>(deploymentRecord, 0, KEY);
  }
}
