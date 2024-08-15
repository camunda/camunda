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
import java.time.Instant;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class CloudEventRequestDto {

  // required properties
  @NotBlank @EqualsAndHashCode.Include @ToString.Include private String id;

  @NotBlank
  @Pattern(
      regexp = "^(?!camunda$).*",
      flags = Pattern.Flag.CASE_INSENSITIVE,
      message = "field must not equal 'camunda'")
  @EqualsAndHashCode.Include
  @ToString.Include
  private String source;

  @NotNull
  @Pattern(regexp = "1\\.0")
  @EqualsAndHashCode.Include
  @ToString.Include
  // Note: it's intended to not use camelCase names here to comply with CloudEvents naming
  // conventions
  // https://github.com/cloudevents/spec/blob/v1.0/spec.md#attribute-naming-convention
  private String specversion;

  @NotBlank @EqualsAndHashCode.Include @ToString.Include private String type;

  // optional properties
  @ToString.Include
  @JsonDeserialize(using = CustomCloudEventTimeDeserializer.class)
  private Instant time;

  private Object data;

  // custom/extension properties
  @NotBlank @ToString.Include
  // Note: it's intended to not use camelCase names here to comply with CloudEvents naming
  // conventions
  // https://github.com/cloudevents/spec/blob/v1.0/spec.md#attribute-naming-convention
  private String traceid;

  @ToString.Include private String group;

  public Optional<Instant> getTime() {
    return Optional.ofNullable(time);
  }

  public Optional<String> getGroup() {
    return Optional.ofNullable(group);
  }

  public Optional<Object> getData() {
    return Optional.ofNullable(data);
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
}
