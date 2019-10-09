/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants(asEnum = true)
public class UserDto extends IdentityDto {
  private String firstName;
  private String lastName;
  private String name;
  private String email;

  public UserDto(final String id) {
    this(id, null, null, null);
  }

  public UserDto(final String id, final String firstName, final String lastName, final String email) {
    super(id, IdentityType.USER);
    this.firstName = firstName;
    this.lastName = lastName;
    this.name = Stream.of(firstName, lastName).filter(Objects::nonNull)
      .collect(collectingAndThen(Collectors.joining(" "), s -> StringUtils.isNotBlank(s) ? s.trim() : null));
    this.email = email;
  }
}
