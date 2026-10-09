/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import java.util.Arrays;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NullMarked;

/**
 * Waits for a membership a test assigned through the client to become readable from secondary
 * storage.
 *
 * <p>An assign command completes once the engine has processed it; the exporter writes and indexes
 * the record afterwards. Anything that reads the membership back through secondary storage — a
 * search request, or an authorization check performed outside the engine, such as the one {@code
 * SecretServices} runs against the authorization index — can therefore still see the state from
 * before the assignment. A test that assigns imperatively and then makes such a call has to wait
 * for the gap to close, or it races the exporter.
 *
 * <p>Tests that declare their entities with {@link UserDefinition} and friends do not need this:
 * {@code EntityManager} already applies the equivalent wait for everything it creates. This is for
 * the imperative case, which bypasses that.
 */
@NullMarked
public final class MembershipVisibility {

  private MembershipVisibility() {}

  /**
   * Waits until every one of {@code usernames} is readable as a member of {@code roleId}.
   *
   * <p>Asserts containment rather than an exact member list, so it stays correct for a role that
   * other users are also assigned to. A test that needs the exact membership should assert that
   * itself once this returns.
   *
   * @param client a client authorized to read the role's users
   * @param roleId the role the users were assigned to
   * @param usernames the usernames to wait for
   */
  public static void awaitUsersVisibleInRole(
      final CamundaClient client, final String roleId, final String... usernames) {
    // exceptions are retried rather than propagated: until the role itself is indexed the search
    // can fail outright, which is the very state this waits out
    Awaitility.await(
            "until users %s are visible in role '%s'".formatted(Arrays.toString(usernames), roleId))
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(client.newUsersByRoleSearchRequest(roleId).send().join().items())
                    .extracting(RoleUser::getUsername)
                    .contains(usernames));
  }
}
