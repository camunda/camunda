/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.service.util.mapper.CustomCloudEventTimeDeserializer;

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
public class CloudEventRequestDto {
  // required properties
  @NotBlank
  @EqualsAndHashCode.Include
  @ToString.Include
  private String id;
  @NotBlank
  @Pattern(regexp = "^(?!camunda$).*", flags = Pattern.Flag.CASE_INSENSITIVE, message = "field must not equal 'camunda'")
  @EqualsAndHashCode.Include
  @ToString.Include
  private String source;
  @NotNull
  @Pattern(regexp = "1\\.0")
  @EqualsAndHashCode.Include
  @ToString.Include
  // Note: it's intended to not use camelCase names here to comply with CloudEvents naming conventions
  // https://github.com/cloudevents/spec/blob/v1.0/spec.md#attribute-naming-convention
  private String specversion;
  @NotBlank
  @EqualsAndHashCode.Include
  @ToString.Include
  private String type;

  // optional properties
  @ToString.Include
  @JsonDeserialize(using = CustomCloudEventTimeDeserializer.class)
  private Instant time;

  private Object data;

  // custom/extension properties
  @NotBlank
  @ToString.Include
  // Note: it's intended to not use camelCase names here to comply with CloudEvents naming conventions
  // https://github.com/cloudevents/spec/blob/v1.0/spec.md#attribute-naming-convention
  private String traceid;
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
