/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import java.util.List;

public class ProcessOverviewResponseDto {

  private String processDefinitionName;
  private String processDefinitionKey;
  private ProcessOwnerResponseDto owner;
  private ProcessDigestResponseDto digest;
  private List<KpiResultDto> kpis;

  public ProcessOverviewResponseDto(
      final String processDefinitionName,
      final String processDefinitionKey,
      final ProcessOwnerResponseDto owner,
      final ProcessDigestResponseDto digest,
      final List<KpiResultDto> kpis) {
    this.processDefinitionName = processDefinitionName;
    this.processDefinitionKey = processDefinitionKey;
    this.owner = owner;
    this.digest = digest;
    this.kpis = kpis;
  }

  public ProcessOverviewResponseDto() {}

  public String getProcessDefinitionName() {
    return processDefinitionName;
  }

  public void setProcessDefinitionName(final String processDefinitionName) {
    this.processDefinitionName = processDefinitionName;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public ProcessOwnerResponseDto getOwner() {
    return owner;
  }

  public void setOwner(final ProcessOwnerResponseDto owner) {
    this.owner = owner;
  }

  public ProcessDigestResponseDto getDigest() {
    return digest;
  }

  public void setDigest(final ProcessDigestResponseDto digest) {
    this.digest = digest;
  }

  public List<KpiResultDto> getKpis() {
    return kpis;
  }

  public void setKpis(final List<KpiResultDto> kpis) {
    this.kpis = kpis;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessOverviewResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $processDefinitionName = getProcessDefinitionName();
    result =
        result * PRIME + ($processDefinitionName == null ? 43 : $processDefinitionName.hashCode());
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $owner = getOwner();
    result = result * PRIME + ($owner == null ? 43 : $owner.hashCode());
    final Object $digest = getDigest();
    result = result * PRIME + ($digest == null ? 43 : $digest.hashCode());
    final Object $kpis = getKpis();
    result = result * PRIME + ($kpis == null ? 43 : $kpis.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessOverviewResponseDto)) {
      return false;
    }
    final ProcessOverviewResponseDto other = (ProcessOverviewResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$processDefinitionName = getProcessDefinitionName();
    final Object other$processDefinitionName = other.getProcessDefinitionName();
    if (this$processDefinitionName == null
        ? other$processDefinitionName != null
        : !this$processDefinitionName.equals(other$processDefinitionName)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$owner = getOwner();
    final Object other$owner = other.getOwner();
    if (this$owner == null ? other$owner != null : !this$owner.equals(other$owner)) {
      return false;
    }
    final Object this$digest = getDigest();
    final Object other$digest = other.getDigest();
    if (this$digest == null ? other$digest != null : !this$digest.equals(other$digest)) {
      return false;
    }
    final Object this$kpis = getKpis();
    final Object other$kpis = other.getKpis();
    if (this$kpis == null ? other$kpis != null : !this$kpis.equals(other$kpis)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ProcessOverviewResponseDto(processDefinitionName="
        + getProcessDefinitionName()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", owner="
        + getOwner()
        + ", digest="
        + getDigest()
        + ", kpis="
        + getKpis()
        + ")";
  }

  public static final class Fields {

    public static final String processDefinitionName = "processDefinitionName";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String owner = "owner";
    public static final String digest = "digest";
    public static final String kpis = "kpis";
  }
}
