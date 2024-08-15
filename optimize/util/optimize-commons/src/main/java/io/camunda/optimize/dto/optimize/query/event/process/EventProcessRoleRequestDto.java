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

public class EventProcessRoleRequestDto<T extends IdentityDto> {

  private static final String ID_SEGMENT_SEPARATOR = ":";

  private String id;

  private T identity;

  public EventProcessRoleRequestDto(final T identity) {
    id = convertIdentityToRoleId(identity);
    this.identity = identity;
  }

  protected EventProcessRoleRequestDto() {}

  public String getId() {
    return Optional.ofNullable(id).orElse(convertIdentityToRoleId(identity));
  }

  protected void setId(final String id) {
    this.id = id;
  }

  private String convertIdentityToRoleId(final T identity) {
    return identity.getType() == null
        ? "UNKNOWN" + ID_SEGMENT_SEPARATOR + identity.getId()
        : identity.getType().name() + ID_SEGMENT_SEPARATOR + identity.getId();
  }

  public T getIdentity() {
    return identity;
  }

  public void setIdentity(final T identity) {
    this.identity = identity;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventProcessRoleRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $identity = getIdentity();
    result = result * PRIME + ($identity == null ? 43 : $identity.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventProcessRoleRequestDto)) {
      return false;
    }
    final EventProcessRoleRequestDto<?> other = (EventProcessRoleRequestDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$identity = getIdentity();
    final Object other$identity = other.getIdentity();
    if (this$identity == null ? other$identity != null : !this$identity.equals(other$identity)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventProcessRoleRequestDto(id=" + getId() + ", identity=" + getIdentity() + ")";
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String identity = "identity";
  }
}
