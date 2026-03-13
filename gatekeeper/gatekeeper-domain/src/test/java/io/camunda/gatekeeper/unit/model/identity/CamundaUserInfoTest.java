/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.unit.model.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.model.identity.CamundaUserInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CamundaUserInfoTest {

  @Test
  void nullListsAreNormalizedToEmpty() {
    final var info = new CamundaUserInfo("name", "user", "email", null, null, null, null, true);

    assertThat(info.authorizedComponents()).isEmpty();
    assertThat(info.tenants()).isEmpty();
    assertThat(info.groups()).isEmpty();
    assertThat(info.roles()).isEmpty();
  }

  @Test
  void listsAreDefensivelyCopied() {
    final var components = new ArrayList<>(List.of("operate"));
    final var tenants = new ArrayList<>(List.of("t1"));
    final var groups = new ArrayList<>(List.of("g1"));
    final var roles = new ArrayList<>(List.of("r1"));

    final var info =
        new CamundaUserInfo("name", "user", "email", components, tenants, groups, roles, false);

    components.add("tasklist");
    tenants.add("t2");
    groups.add("g2");
    roles.add("r2");

    assertThat(info.authorizedComponents()).containsExactly("operate");
    assertThat(info.tenants()).containsExactly("t1");
    assertThat(info.groups()).containsExactly("g1");
    assertThat(info.roles()).containsExactly("r1");
  }
}
