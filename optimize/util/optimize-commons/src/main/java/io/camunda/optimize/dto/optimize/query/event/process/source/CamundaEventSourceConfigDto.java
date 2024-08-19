/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

import java.util.ArrayList;
import java.util.List;

public class CamundaEventSourceConfigDto extends EventSourceConfigDto {

  private String processDefinitionKey;
  private String processDefinitionName;
  private List<String> versions = new ArrayList<>();
  private List<String> tenants = new ArrayList<>();
  private boolean tracedByBusinessKey;
  private String traceVariable;

  public CamundaEventSourceConfigDto() {}

  protected CamundaEventSourceConfigDto(final CamundaEventSourceConfigDtoBuilder<?, ?> b) {
    super();
    processDefinitionKey = b.processDefinitionKey;
    processDefinitionName = b.processDefinitionName;
    if (b.versions$set) {
      versions = b.versions$value;
    } else {
      versions = $default$versions();
    }
    if (b.tenants$set) {
      tenants = b.tenants$value;
    } else {
      tenants = $default$tenants();
    }
    tracedByBusinessKey = b.tracedByBusinessKey;
    traceVariable = b.traceVariable;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionName() {
    return processDefinitionName;
  }

  public void setProcessDefinitionName(final String processDefinitionName) {
    this.processDefinitionName = processDefinitionName;
  }

  public List<String> getVersions() {
    return versions;
  }

  public void setVersions(final List<String> versions) {
    this.versions = versions;
  }

  public List<String> getTenants() {
    return tenants;
  }

  public void setTenants(final List<String> tenants) {
    this.tenants = tenants;
  }

  public boolean isTracedByBusinessKey() {
    return tracedByBusinessKey;
  }

  public void setTracedByBusinessKey(final boolean tracedByBusinessKey) {
    this.tracedByBusinessKey = tracedByBusinessKey;
  }

  public String getTraceVariable() {
    return traceVariable;
  }

  public void setTraceVariable(final String traceVariable) {
    this.traceVariable = traceVariable;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof CamundaEventSourceConfigDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionName = getProcessDefinitionName();
    result =
        result * PRIME + ($processDefinitionName == null ? 43 : $processDefinitionName.hashCode());
    final Object $versions = getVersions();
    result = result * PRIME + ($versions == null ? 43 : $versions.hashCode());
    final Object $tenants = getTenants();
    result = result * PRIME + ($tenants == null ? 43 : $tenants.hashCode());
    result = result * PRIME + (isTracedByBusinessKey() ? 79 : 97);
    final Object $traceVariable = getTraceVariable();
    result = result * PRIME + ($traceVariable == null ? 43 : $traceVariable.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CamundaEventSourceConfigDto)) {
      return false;
    }
    final CamundaEventSourceConfigDto other = (CamundaEventSourceConfigDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$processDefinitionName = getProcessDefinitionName();
    final Object other$processDefinitionName = other.getProcessDefinitionName();
    if (this$processDefinitionName == null
        ? other$processDefinitionName != null
        : !this$processDefinitionName.equals(other$processDefinitionName)) {
      return false;
    }
    final Object this$versions = getVersions();
    final Object other$versions = other.getVersions();
    if (this$versions == null ? other$versions != null : !this$versions.equals(other$versions)) {
      return false;
    }
    final Object this$tenants = getTenants();
    final Object other$tenants = other.getTenants();
    if (this$tenants == null ? other$tenants != null : !this$tenants.equals(other$tenants)) {
      return false;
    }
    if (isTracedByBusinessKey() != other.isTracedByBusinessKey()) {
      return false;
    }
    final Object this$traceVariable = getTraceVariable();
    final Object other$traceVariable = other.getTraceVariable();
    if (this$traceVariable == null
        ? other$traceVariable != null
        : !this$traceVariable.equals(other$traceVariable)) {
      return false;
    }
    return true;
  }

  private static List<String> $default$versions() {
    return new ArrayList<>();
  }

  private static List<String> $default$tenants() {
    return new ArrayList<>();
  }

  public static CamundaEventSourceConfigDtoBuilder<?, ?> builder() {
    return new CamundaEventSourceConfigDtoBuilderImpl();
  }

  public static final class Fields {

    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String processDefinitionName = "processDefinitionName";
    public static final String versions = "versions";
    public static final String tenants = "tenants";
    public static final String tracedByBusinessKey = "tracedByBusinessKey";
    public static final String traceVariable = "traceVariable";
  }

  public abstract static class CamundaEventSourceConfigDtoBuilder<
          C extends CamundaEventSourceConfigDto, B extends CamundaEventSourceConfigDtoBuilder<C, B>>
      extends EventSourceConfigDtoBuilder<C, B> {

    private String processDefinitionKey;
    private String processDefinitionName;
    private List<String> versions$value;
    private boolean versions$set;
    private List<String> tenants$value;
    private boolean tenants$set;
    private boolean tracedByBusinessKey;
    private String traceVariable;

    public B processDefinitionKey(final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return self();
    }

    public B processDefinitionName(final String processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return self();
    }

    public B versions(final List<String> versions) {
      versions$value = versions;
      versions$set = true;
      return self();
    }

    public B tenants(final List<String> tenants) {
      tenants$value = tenants;
      tenants$set = true;
      return self();
    }

    public B tracedByBusinessKey(final boolean tracedByBusinessKey) {
      this.tracedByBusinessKey = tracedByBusinessKey;
      return self();
    }

    public B traceVariable(final String traceVariable) {
      this.traceVariable = traceVariable;
      return self();
    }

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "CamundaEventSourceConfigDto.CamundaEventSourceConfigDtoBuilder(super="
          + super.toString()
          + ", processDefinitionKey="
          + processDefinitionKey
          + ", processDefinitionName="
          + processDefinitionName
          + ", versions$value="
          + versions$value
          + ", tenants$value="
          + tenants$value
          + ", tracedByBusinessKey="
          + tracedByBusinessKey
          + ", traceVariable="
          + traceVariable
          + ")";
    }
  }

  private static final class CamundaEventSourceConfigDtoBuilderImpl
      extends CamundaEventSourceConfigDtoBuilder<
          CamundaEventSourceConfigDto, CamundaEventSourceConfigDtoBuilderImpl> {

    private CamundaEventSourceConfigDtoBuilderImpl() {}

    @Override
    protected CamundaEventSourceConfigDtoBuilderImpl self() {
      return this;
    }

    @Override
    public CamundaEventSourceConfigDto build() {
      return new CamundaEventSourceConfigDto(this);
    }
  }
}
