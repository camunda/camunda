/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

public class DefinitionVersionResponseDto {

  private String version;
  private String versionTag;

  public DefinitionVersionResponseDto(final String version, final String versionTag) {
    this.version = version;
    this.versionTag = versionTag;
  }

  protected DefinitionVersionResponseDto() {}

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public void setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionVersionResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $version = getVersion();
    result = result * PRIME + ($version == null ? 43 : $version.hashCode());
    final Object $versionTag = getVersionTag();
    result = result * PRIME + ($versionTag == null ? 43 : $versionTag.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DefinitionVersionResponseDto)) {
      return false;
    }
    final DefinitionVersionResponseDto other = (DefinitionVersionResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$version = getVersion();
    final Object other$version = other.getVersion();
    if (this$version == null ? other$version != null : !this$version.equals(other$version)) {
      return false;
    }
    final Object this$versionTag = getVersionTag();
    final Object other$versionTag = other.getVersionTag();
    if (this$versionTag == null
        ? other$versionTag != null
        : !this$versionTag.equals(other$versionTag)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DefinitionVersionResponseDto(version="
        + getVersion()
        + ", versionTag="
        + getVersionTag()
        + ")";
  }

  public static DefinitionVersionResponseDtoBuilder builder() {
    return new DefinitionVersionResponseDtoBuilder();
  }

  public static class DefinitionVersionResponseDtoBuilder {

    private String version;
    private String versionTag;

    DefinitionVersionResponseDtoBuilder() {}

    public DefinitionVersionResponseDtoBuilder version(final String version) {
      this.version = version;
      return this;
    }

    public DefinitionVersionResponseDtoBuilder versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    public DefinitionVersionResponseDto build() {
      return new DefinitionVersionResponseDto(version, versionTag);
    }

    @Override
    public String toString() {
      return "DefinitionVersionResponseDto.DefinitionVersionResponseDtoBuilder(version="
          + version
          + ", versionTag="
          + versionTag
          + ")";
    }
  }
}
