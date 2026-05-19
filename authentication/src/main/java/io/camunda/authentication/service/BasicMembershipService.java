/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

/**
 * Produces {@link MembershipResolver}s for BASIC-authenticated principals. BASIC has no token
 * claims, so resolvers only expose the DB-backed groups/roles/tenants chain; mapping rules are
 * always empty and the converter should not wire them.
 */
public interface BasicMembershipService {

  /**
   * @param username the BASIC principal whose memberships should be resolved
   */
  MembershipResolver newResolver(String username);
}
