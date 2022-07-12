/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants
@ToString(callSuper = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class UserDto extends IdentityWithMetadataResponseDto {
  private String firstName;
  private String lastName;
  private String email;

  // optional, only available in C8 SaaS
  private List<String> roles;

  public UserDto(final String id) {
    this(id, null, null, null, null);
  }

  public UserDto(final String id, final String firstName) {
    this(id, firstName, null, null, null);
  }

  public UserDto(final String id, final String firstName, final String lastName, final String email) {
    this(id, firstName, lastName, email, null);
  }

  public UserDto(final String id, final String fullName, final String email, final List<String> roles) {
    this(id, fullName, null, email, roles);
  }

  @JsonCreator
  public UserDto(@JsonProperty(required = true, value = "id") @NonNull final String id,
                 @JsonProperty(required = false, value = "firstName") final String firstName,
                 @JsonProperty(required = false, value = "lastName") final String lastName,
                 @JsonProperty(required = false, value = "email") final String email,
                 @JsonProperty(required = false, value = "roles") final List<String> roles) {
    super(id, IdentityType.USER, resolveName(id, firstName, lastName));
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.roles = roles;
  }

  private static String resolveName(final String id, final String firstName, final String lastName) {
    return Stream.of(firstName, lastName)
      .filter(Objects::nonNull)
      .collect(collectingAndThen(Collectors.joining(" "), s -> StringUtils.isNotBlank(s) ? s.trim() : id));
  }
}
