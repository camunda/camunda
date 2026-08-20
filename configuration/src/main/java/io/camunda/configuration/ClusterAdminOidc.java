/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.List;

/**
 * OIDC authorization for the cluster-admin API. Bearer tokens are validated against the default
 * cluster-wide OIDC provider ({@code camunda.security.authentication.oidc.*}); a client is granted
 * full cluster-admin access when the token matches any configured client id, group, or claim.
 */
public class ClusterAdminOidc {

  /** Client ids granted cluster-admin, matched against the provider's {@code client-id-claim}. */
  private List<String> clients = List.of();

  /** Groups granted cluster-admin, matched against the provider's {@code groups-claim}. */
  private List<String> groups = List.of();

  /** Generic {@code name}/{@code value} claim matchers granting cluster-admin. */
  private List<ClusterAdminClaim> claims = List.of();

  public List<String> getClients() {
    return clients;
  }

  public void setClients(final List<String> clients) {
    this.clients = clients == null ? List.of() : clients;
  }

  public List<String> getGroups() {
    return groups;
  }

  public void setGroups(final List<String> groups) {
    this.groups = groups == null ? List.of() : groups;
  }

  public List<ClusterAdminClaim> getClaims() {
    return claims;
  }

  public void setClaims(final List<ClusterAdminClaim> claims) {
    this.claims = claims == null ? List.of() : claims;
  }
}
