/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.IdentityDto;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventProcessRoleRequestDto<T extends IdentityDto> {

  private static final String ID_SEGMENT_SEPARATOR = ":";

  @Setter(value = AccessLevel.PROTECTED)
  private String id;

  private T identity;

  public EventProcessRoleRequestDto(final T identity) {
    id = convertIdentityToRoleId(identity);
    this.identity = identity;
  }

  public String getId() {
    return Optional.ofNullable(id).orElse(convertIdentityToRoleId(identity));
  }

  private String convertIdentityToRoleId(final T identity) {
    return identity.getType() == null
        ? "UNKNOWN" + ID_SEGMENT_SEPARATOR + identity.getId()
        : identity.getType().name() + ID_SEGMENT_SEPARATOR + identity.getId();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String identity = "identity";
  }
}
