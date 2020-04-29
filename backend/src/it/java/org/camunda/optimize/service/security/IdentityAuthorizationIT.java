/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.Lists;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_GROUP;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class IdentityAuthorizationIT extends AbstractIT {

  @Test
  public void searchingForAllIdentitiesIsFilteredByAuthorizations() {
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
    authorizationClient.grantSingleResourceAuthorizationForKermit(groupIdentity1.getId(), RESOURCE_TYPE_GROUP);
    authorizationClient.grantSingleResourceAuthorizationForKermit(userIdentity1.getId(), RESOURCE_TYPE_USER);

    // when
    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildSearchForIdentities("")
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());

    // then only return identities the current user has access to
    assertThat(searchResult)
      .isEqualTo(
        new IdentitySearchResultDto(
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

    // then access is forbidden
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
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

    // then access is forbidden
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }
}
