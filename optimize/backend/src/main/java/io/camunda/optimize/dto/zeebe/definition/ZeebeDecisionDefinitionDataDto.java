/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.definition;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import org.apache.commons.lang3.StringUtils;

public class ZeebeDecisionDefinitionDataDto
    implements DecisionRequirementsMetadataValue, RecordValue {

  private byte[] resource;
  private long decisionRequirementsKey;
  private String decisionRequirementsId;
  private int decisionRequirementsVersion;
  private String decisionRequirementsName;
  private byte[] checksum;
  private String resourceName;
  private boolean duplicate;
  private String tenantId;
  private String namespace;

  public ZeebeDecisionDefinitionDataDto() {}

  @Override
  public String getTenantId() {
    return StringUtils.isEmpty(tenantId) ? ZEEBE_DEFAULT_TENANT_ID : tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public byte[] getResource() {
    return resource;
  }

  public void setResource(final byte[] resource) {
    this.resource = resource;
  }

  @Override
  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  @Override
  public String getDecisionRequirementsName() {
    return decisionRequirementsName;
  }

  @Override
  public int getDecisionRequirementsVersion() {
    return decisionRequirementsVersion;
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public void setDecisionRequirementsKey(final long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public byte[] getChecksum() {
    return checksum;
  }

  @Override
  public boolean isDuplicate() {
    // Process Records should never be duplicate in Zeebe
    return false;
  }

  public void setDuplicate(final boolean duplicate) {
    this.duplicate = duplicate;
  }

  public void setChecksum(final byte[] checksum) {
    this.checksum = checksum;
  }

  public void setResourceName(final String resourceName) {
    this.resourceName = resourceName;
  }

  public void setNamespace(final String namespace) {
    this.namespace = namespace;
  }

  public void setDecisionRequirementsVersion(final int decisionRequirementsVersion) {
    this.decisionRequirementsVersion = decisionRequirementsVersion;
  }

  public void setDecisionRequirementsName(final String decisionRequirementsName) {
    this.decisionRequirementsName = decisionRequirementsName;
  }

  public void setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeDecisionDefinitionDataDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "toStringblahblah";
  }
}
