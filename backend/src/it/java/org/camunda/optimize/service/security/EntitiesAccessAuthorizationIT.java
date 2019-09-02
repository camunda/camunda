/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class EntitiesAccessAuthorizationIT extends AbstractCollectionRoleIT {

  @Test
  @Parameters(method = ACCESS_IDENTITY_ROLES)
  public void containsAuthorizedCollectionsByCollectionUserRole(final IdentityAndRole accessIdentityRolePairs) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(
      accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId
    );

    // when
    final List<EntityDto> authorizedEntities = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, 200);

    // then
    assertThat(authorizedEntities.size(), is(1));
    assertThat(
      authorizedEntities.stream().map(EntityDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(collectionId)
    );
    assertThat(
      authorizedEntities.stream().map(EntityDto::getCurrentUserRole).collect(Collectors.toList()),
      contains(accessIdentityRolePairs.roleType)
    );
  }

  @Test
  public void unauthorizedCollectionNotAvailable() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    createNewCollectionAsDefaultUser();

    // when
    final List<EntityDto> authorizedEntities = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, 200);

    // then
    assertThat(authorizedEntities.size(), is(0));
  }

}
