/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DefinitionVersionResponseDto that = (DefinitionVersionResponseDto) o;
    return Objects.equals(version, that.version) && Objects.equals(versionTag, that.versionTag);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, versionTag);
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
