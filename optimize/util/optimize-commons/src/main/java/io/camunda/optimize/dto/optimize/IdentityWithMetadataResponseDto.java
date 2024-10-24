/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = UserDto.class, name = "user"),
  @JsonSubTypes.Type(value = GroupDto.class, name = "group"),
})
public abstract class IdentityWithMetadataResponseDto extends IdentityDto {

  private String name;

  public IdentityWithMetadataResponseDto(final String id, final IdentityType type) {
    this(id, type, null);
  }

  public IdentityWithMetadataResponseDto(
      final String id, final IdentityType type, final String name) {
    super(id, type);
    this.name = name;
  }

  protected IdentityWithMetadataResponseDto() {}

  @JsonIgnore
  protected abstract List<Supplier<String>> getSearchableDtoFields();

  public IdentityDto toIdentityDto() {
    return new IdentityDto(getId(), getType());
  }

  @JsonIgnore
  public boolean isIdentityContainsSearchTerm(final String searchTerm) {
    return StringUtils.isBlank(searchTerm)
        || getSearchableDtoFields().stream()
            .anyMatch(
                searchableField ->
                    StringUtils.isNotBlank(searchableField.get())
                        && StringUtils.containsAnyIgnoreCase(searchableField.get(), searchTerm));
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof IdentityWithMetadataResponseDto;
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
    return "IdentityWithMetadataResponseDto(super="
        + super.toString()
        + ", name="
        + getName()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String name = "name";
  }
}
