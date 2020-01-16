/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Optional;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@FieldNameConstants
public class CloudEventDto {
  // required properties
  @NotBlank
  @EqualsAndHashCode.Include
  @ToString.Include
  private String id;
  @NotBlank
  @EqualsAndHashCode.Include
  @ToString.Include
  private String source;
  @NotNull
  @Pattern(regexp = "1\\.0")
  @EqualsAndHashCode.Include
  @ToString.Include
  private String specversion;
  @NotBlank
  @EqualsAndHashCode.Include
  @ToString.Include
  private String type;

  // optional properties
  @ToString.Include
  private Instant time;

  private Object data;

  // custom/extension properties
  @NotBlank
  @ToString.Include
  private String traceId;
  @ToString.Include
  private String group;

  public Optional<Instant> getTime() {
    return Optional.ofNullable(time);
  }

  public Optional<String> getGroup() {
    return Optional.ofNullable(group);
  }

  public Optional<Object> getData() {
    return Optional.ofNullable(data);
  }
}
