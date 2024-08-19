/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine;

import java.util.HashSet;
import java.util.Set;

public class AuthorizedIdentitiesResult {

  boolean globalOptimizeGrant = false;
  final Set<String> grantedGroupIds = new HashSet<>();
  final Set<String> revokedGroupIds = new HashSet<>();
  final Set<String> grantedUserIds = new HashSet<>();
  final Set<String> revokedUserIds = new HashSet<>();

  public AuthorizedIdentitiesResult() {}

  public boolean isGlobalOptimizeGrant() {
    return globalOptimizeGrant;
  }

  public void setGlobalOptimizeGrant(final boolean globalOptimizeGrant) {
    this.globalOptimizeGrant = globalOptimizeGrant;
  }

  public Set<String> getGrantedGroupIds() {
    return grantedGroupIds;
  }

  public Set<String> getRevokedGroupIds() {
    return revokedGroupIds;
  }

  public Set<String> getGrantedUserIds() {
    return grantedUserIds;
  }

  public Set<String> getRevokedUserIds() {
    return revokedUserIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizedIdentitiesResult;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isGlobalOptimizeGrant() ? 79 : 97);
    final Object $grantedGroupIds = getGrantedGroupIds();
    result = result * PRIME + ($grantedGroupIds == null ? 43 : $grantedGroupIds.hashCode());
    final Object $revokedGroupIds = getRevokedGroupIds();
    result = result * PRIME + ($revokedGroupIds == null ? 43 : $revokedGroupIds.hashCode());
    final Object $grantedUserIds = getGrantedUserIds();
    result = result * PRIME + ($grantedUserIds == null ? 43 : $grantedUserIds.hashCode());
    final Object $revokedUserIds = getRevokedUserIds();
    result = result * PRIME + ($revokedUserIds == null ? 43 : $revokedUserIds.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AuthorizedIdentitiesResult)) {
      return false;
    }
    final AuthorizedIdentitiesResult other = (AuthorizedIdentitiesResult) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isGlobalOptimizeGrant() != other.isGlobalOptimizeGrant()) {
      return false;
    }
    final Object this$grantedGroupIds = getGrantedGroupIds();
    final Object other$grantedGroupIds = other.getGrantedGroupIds();
    if (this$grantedGroupIds == null
        ? other$grantedGroupIds != null
        : !this$grantedGroupIds.equals(other$grantedGroupIds)) {
      return false;
    }
    final Object this$revokedGroupIds = getRevokedGroupIds();
    final Object other$revokedGroupIds = other.getRevokedGroupIds();
    if (this$revokedGroupIds == null
        ? other$revokedGroupIds != null
        : !this$revokedGroupIds.equals(other$revokedGroupIds)) {
      return false;
    }
    final Object this$grantedUserIds = getGrantedUserIds();
    final Object other$grantedUserIds = other.getGrantedUserIds();
    if (this$grantedUserIds == null
        ? other$grantedUserIds != null
        : !this$grantedUserIds.equals(other$grantedUserIds)) {
      return false;
    }
    final Object this$revokedUserIds = getRevokedUserIds();
    final Object other$revokedUserIds = other.getRevokedUserIds();
    if (this$revokedUserIds == null
        ? other$revokedUserIds != null
        : !this$revokedUserIds.equals(other$revokedUserIds)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AuthorizedIdentitiesResult(globalOptimizeGrant="
        + isGlobalOptimizeGrant()
        + ", grantedGroupIds="
        + getGrantedGroupIds()
        + ", revokedGroupIds="
        + getRevokedGroupIds()
        + ", grantedUserIds="
        + getGrantedUserIds()
        + ", revokedUserIds="
        + getRevokedUserIds()
        + ")";
  }
}
