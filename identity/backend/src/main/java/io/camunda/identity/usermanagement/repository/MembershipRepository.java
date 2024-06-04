/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.repository;

import io.camunda.identity.usermanagement.CamundaGroup;
import io.camunda.identity.usermanagement.model.Membership;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

  @Query(
      value =
          "select new io.camunda.identity.usermanagement.CamundaGroup(gm.group.id, gm.group.name)"
              + " from Membership gm"
              + " where gm.username = :username")
  List<CamundaGroup> loadUserGroups(@Param("username") final String username);
}
