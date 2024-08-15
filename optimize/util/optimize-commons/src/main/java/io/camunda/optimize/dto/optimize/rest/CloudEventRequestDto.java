/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.optimize.service.util.mapper.CustomCloudEventTimeDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Pattern.Flag;
import java.time.Instant;
import java.util.Optional;

public class CloudEventRequestDto {

  // required properties
  @NotBlank
  private String id;

  @NotBlank
  @Pattern(
      regexp = "^(?!camunda$).*",
      flags = Pattern.Flag.CASE_INSENSITIVE,
      message = "field must not equal 'camunda'")
  private String source;

  @NotNull
  @Pattern(regexp = "1\\.0")
  // Note: it's intended to not use camelCase names here to comply with CloudEvents naming
  // conventions
  // https://github.com/cloudevents/spec/blob/v1.0/spec.md#attribute-naming-convention
  private String specversion;

  @NotBlank
  private String type;

  // optional properties
  @JsonDeserialize(using = CustomCloudEventTimeDeserializer.class)
  private Instant time;

  private Object data;

  // custom/extension properties
  @NotBlank
  // Note: it's intended to not use camelCase names here to comply with CloudEvents naming
  // conventions
  // https://github.com/cloudevents/spec/blob/v1.0/spec.md#attribute-naming-convention
  private String traceid;

  private String group;

  public CloudEventRequestDto(@NotBlank final String id, @NotBlank @Pattern(
      regexp = "^(?!camunda$).*",
      flags = Flag.CASE_INSENSITIVE,
      message = "field must not equal 'camunda'") final String source,
      @NotNull @Pattern(regexp = "1\\.0") final String specversion, @NotBlank final String type,
      final Instant time,
      final Object data, @NotBlank final String traceid, final String group) {
    this.id = id;
    this.source = source;
    this.specversion = specversion;
    this.type = type;
    this.time = time;
    this.data = data;
    this.traceid = traceid;
    this.group = group;
  }

  public CloudEventRequestDto() {
  }

  public Optional<Instant> getTime() {
    return Optional.ofNullable(time);
  }

  @JsonDeserialize(using = CustomCloudEventTimeDeserializer.class)
  public void setTime(final Instant time) {
    this.time = time;
  }

  public Optional<String> getGroup() {
    return Optional.ofNullable(group);
  }

  public void setGroup(final String group) {
    this.group = group;
  }

  public Optional<Object> getData() {
    return Optional.ofNullable(data);
  }

  public void setData(final Object data) {
    this.data = data;
  }

  public @NotBlank String getId() {
    return id;
  }

  public void setId(@NotBlank final String id) {
    this.id = id;
  }

  public @NotBlank @Pattern(
      regexp = "^(?!camunda$).*",
      flags = Flag.CASE_INSENSITIVE,
      message = "field must not equal 'camunda'") String getSource() {
    return source;
  }

  public void setSource(@NotBlank @Pattern(
      regexp = "^(?!camunda$).*",
      flags = Flag.CASE_INSENSITIVE,
      message = "field must not equal 'camunda'") final String source) {
    this.source = source;
  }

  public @NotNull @Pattern(regexp = "1\\.0") String getSpecversion() {
    return specversion;
  }

  public void setSpecversion(@NotNull @Pattern(regexp = "1\\.0") final String specversion) {
    this.specversion = specversion;
  }

  public @NotBlank String getType() {
    return type;
  }

  public void setType(@NotBlank final String type) {
    this.type = type;
  }

  public @NotBlank String getTraceid() {
    return traceid;
  }

  public void setTraceid(@NotBlank final String traceid) {
    this.traceid = traceid;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CloudEventRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $source = getSource();
    result = result * PRIME + ($source == null ? 43 : $source.hashCode());
    final Object $specversion = getSpecversion();
    result = result * PRIME + ($specversion == null ? 43 : $specversion.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CloudEventRequestDto)) {
      return false;
    }
    final CloudEventRequestDto other = (CloudEventRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$source = getSource();
    final Object other$source = other.getSource();
    if (this$source == null ? other$source != null : !this$source.equals(other$source)) {
      return false;
    }
    final Object this$specversion = getSpecversion();
    final Object other$specversion = other.getSpecversion();
    if (this$specversion == null ? other$specversion != null
        : !this$specversion.equals(other$specversion)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CloudEventRequestDto(id=" + getId() + ", source=" + getSource()
        + ", specversion=" + getSpecversion() + ", type=" + getType() + ", time="
        + getTime() + ", traceid=" + getTraceid() + ", group=" + getGroup() + ")";
  }

  public static CloudEventRequestDtoBuilder builder() {
    return new CloudEventRequestDtoBuilder();
  }

  public CloudEventRequestDtoBuilder toBuilder() {
    return new CloudEventRequestDtoBuilder().id(id).source(source)
        .specversion(specversion).type(type).time(time).data(data)
        .traceid(traceid).group(group);
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String source = "source";
    public static final String specversion = "specversion";
    public static final String type = "type";
    public static final String time = "time";
    public static final String data = "data";
    public static final String traceid = "traceid";
    public static final String group = "group";
  }

  public static class CloudEventRequestDtoBuilder {

    private @NotBlank String id;
    private @NotBlank
    @Pattern(
        regexp = "^(?!camunda$).*",
        flags = Flag.CASE_INSENSITIVE,
        message = "field must not equal 'camunda'") String source;
    private @NotNull
    @Pattern(regexp = "1\\.0") String specversion;
    private @NotBlank String type;
    private Instant time;
    private Object data;
    private @NotBlank String traceid;
    private String group;

    CloudEventRequestDtoBuilder() {
    }

    public CloudEventRequestDtoBuilder id(@NotBlank final String id) {
      this.id = id;
      return this;
    }

    public CloudEventRequestDtoBuilder source(@NotBlank @Pattern(
        regexp = "^(?!camunda$).*",
        flags = Flag.CASE_INSENSITIVE,
        message = "field must not equal 'camunda'") final String source) {
      this.source = source;
      return this;
    }

    public CloudEventRequestDtoBuilder specversion(
        @NotNull @Pattern(regexp = "1\\.0") final String specversion) {
      this.specversion = specversion;
      return this;
    }

    public CloudEventRequestDtoBuilder type(@NotBlank final String type) {
      this.type = type;
      return this;
    }

    @JsonDeserialize(using = CustomCloudEventTimeDeserializer.class)
    public CloudEventRequestDtoBuilder time(final Instant time) {
      this.time = time;
      return this;
    }

    public CloudEventRequestDtoBuilder data(final Object data) {
      this.data = data;
      return this;
    }

    public CloudEventRequestDtoBuilder traceid(@NotBlank final String traceid) {
      this.traceid = traceid;
      return this;
    }

    public CloudEventRequestDtoBuilder group(final String group) {
      this.group = group;
      return this;
    }

    public CloudEventRequestDto build() {
      return new CloudEventRequestDto(id, source, specversion, type, time,
          data, traceid, group);
    }

    @Override
    public String toString() {
      return "CloudEventRequestDto.CloudEventRequestDtoBuilder(id=" + id + ", source="
          + source + ", specversion=" + specversion + ", type=" + type + ", time="
          + time + ", data=" + data + ", traceid=" + traceid + ", group="
          + group + ")";
    }
  }
}
