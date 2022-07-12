/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import com.google.common.collect.Lists;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.READ_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_GROUP;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class IdentityAuthorizationIT extends AbstractIT {

  private static Stream<String> relevantPermissions() {
    return Stream.of(ALL_PERMISSION, READ_PERMISSION);
  }

  @ParameterizedTest
  @MethodSource("relevantPermissions")
  public void searchingForAllIdentitiesIsFilteredByAuthorizations(final String permission) {
    // given
    final GroupDto groupIdentity1 = new GroupDto("testGroup1", "Group 1", 4L);
    final GroupDto groupIdentity2 = new GroupDto("testGroup2", "Group 2", 4L);
    final UserDto userIdentity1 =
      new UserDto("testUser1", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    final UserDto userIdentity2 =
      new UserDto("testUser2", "Bilbo", "Baggins", "bilbo.baggins@camunda.com");

    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity1);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity2);
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity1);
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, Collections.singletonList(permission), groupIdentity1.getId(), RESOURCE_TYPE_GROUP
    );
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, Collections.singletonList(permission), userIdentity1.getId(), RESOURCE_TYPE_USER
    );

    // when
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity("", KERMIT_USER, KERMIT_USER);

    // then only return identities the current user has access to are returned
    // To not leak any unauthorized information, the total count reflects only what the user is allowed to see
    assertThat(searchResult)
      .isEqualTo(
        new IdentitySearchResultResponseDto(
          2L, Lists.newArrayList(userIdentity1, groupIdentity1)
        ));
  }

  @Test
  public void getGroupByIdWithoutAuthorizationFails() {
    // given
    final GroupDto notAuthorizedGroup = new GroupDto("testGroup", "A Test Group", 4L);

    embeddedOptimizeExtension.getIdentityService().addIdentity(notAuthorizedGroup);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetIdentityById(notAuthorizedGroup.getId())
      .execute();

    // then assert that result is shown as "Not Found" so that no information about the existence (or not) of a user
    // is leaked to the unauthorized user
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void getUserByIdWithoutAuthorizationFails() {
    // given
    final UserDto notAuthorizedUser =
      new UserDto("testUser2", "Bilbo", "Baggins", "bilbo.baggins@camunda.com");

    embeddedOptimizeExtension.getIdentityService().addIdentity(notAuthorizedUser);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetIdentityById(notAuthorizedUser.getId())
      .execute();

    // then assert that result is shown as "Not Found" so that no information about the existence (or not) of a user
    // is leaked to the unauthorized user
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("relevantPermissions")
  public void limitResults_partialAuthorizations_readPermission(final String permission) {
    // given
    final UserDto user1 = new UserDto("testUser1");
    final UserDto user2 = new UserDto("testUser2");
    final UserDto user3 = new UserDto("testUser3");

    embeddedOptimizeExtension.getIdentityService().addIdentity(user1);
    embeddedOptimizeExtension.getIdentityService().addIdentity(user2);
    embeddedOptimizeExtension.getIdentityService().addIdentity(user3);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, Collections.singletonList(permission), user2.getId(), RESOURCE_TYPE_USER
    );

    // when we search for a term that matches all users, but with a limit of 1 result
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity(
      "testUser",
      1,
      KERMIT_USER,
      KERMIT_USER
    );

    // then the limit of 1 is applied correctly:
    // the search returns the 1 match which kermit is permitted to see.
    // If the limit did not work properly, the result would be empty because the first limited search result returns
    // user1, which kermit is not allowed to see.
    // To not leak any unauthorized information, the total count reflects only what the user is allowed to see
    assertThat(searchResult)
      .isEqualTo(new IdentitySearchResultResponseDto(
        1L, Lists.newArrayList(user2)
      ));
  }

  @ParameterizedTest
  @MethodSource("relevantPermissions")
  public void limitResults_partialAuthorizationsForMultipleResults(final String permission) {
    // given
    final UserDto user1 = new UserDto("testUser1");
    final UserDto user2 = new UserDto("testUser2");
    final UserDto user3 = new UserDto("testUser3");
    final UserDto user4 = new UserDto("testUser4");

    embeddedOptimizeExtension.getIdentityService().addIdentity(user1);
    embeddedOptimizeExtension.getIdentityService().addIdentity(user2);
    embeddedOptimizeExtension.getIdentityService().addIdentity(user3);
    embeddedOptimizeExtension.getIdentityService().addIdentity(user4);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, Collections.singletonList(permission), user2.getId(), RESOURCE_TYPE_USER
    );
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, Collections.singletonList(permission), user4.getId(), RESOURCE_TYPE_USER
    );

    // when we search for a term that matches all users, but with a limit of 2 results
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity(
      "testUser",
      2,
      KERMIT_USER,
      KERMIT_USER
    );

    // then the limit of 2 is applied correctly:
    // The search returns the 2 matches which kermit is permitted to see.
    // If the limit did not work properly, the result would only have user2 in it because the first limited search
    // result returns user1 and user2, and of those kermit is only allowed to see user2.
    // To not leak any unauthorized information, the total count reflects only what the user is allowed to see
    assertThat(searchResult)
      .isEqualTo(new IdentitySearchResultResponseDto(
        2L, Lists.newArrayList(user2, user4)
      ));
  }
}
