/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import java.util.List;
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessOverviewResponseDto that = (ProcessOverviewResponseDto) o;
    return Objects.equals(processDefinitionName, that.processDefinitionName)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(owner, that.owner)
        && Objects.equals(digest, that.digest)
        && Objects.equals(kpis, that.kpis);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processDefinitionName, processDefinitionKey, owner, digest, kpis);
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

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String processDefinitionName = "processDefinitionName";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String owner = "owner";
    public static final String digest = "digest";
    public static final String kpis = "kpis";
  }
}
