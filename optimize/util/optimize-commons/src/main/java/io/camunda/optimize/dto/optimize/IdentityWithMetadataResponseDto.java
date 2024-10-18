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
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof IdentityWithMetadataResponseDto)) {
      return false;
    }
    final IdentityWithMetadataResponseDto other = (IdentityWithMetadataResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "IdentityWithMetadataResponseDto(super="
        + super.toString()
        + ", name="
        + getName()
        + ")";
  }

  public static final class Fields {

    public static final String name = "name";
  }
}
