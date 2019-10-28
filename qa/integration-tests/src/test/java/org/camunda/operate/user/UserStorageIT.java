/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.camunda.operate.entities.UserEntity;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.webapp.rest.exception.NotFoundException;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.webapp.user.UserStorage;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserStorageIT extends OperateIntegrationTest{

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private UserStorage userStorage;
  
  protected List<String> allUsernames(){
    return Arrays.asList("test-user,act,demo".split(","));
  }
  
  protected void deleteAllUsers() {
    allUsernames().stream().forEach(userStorage::deleteById);
  }
  
  protected void assertAllUsersAreDeleted() {
    elasticsearchTestRule.refreshOperateESIndices();
    allUsernames().forEach( username -> {
      assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> userStorage.getByName(username));
    });
  }
  
  @Before
  public void setUp() {
    assertThat(userStorage).isNotNull();
    deleteAllUsers();
    assertAllUsersAreDeleted();
  }
  
  @After
  public void tearDown() {
    deleteAllUsers();
    assertAllUsersAreDeleted();
  }

  @Test
  public void testCreateAndGetByName() {
    assertThatThrownBy(() -> userStorage.getByName("test-user")).isInstanceOf(NotFoundException.class);
    UserEntity user = UserEntity.from("test-user","test-password","USER");
    userStorage.create(user);
    elasticsearchTestRule.refreshOperateESIndices();
    assertThat(userStorage.getByName("test-user")).isEqualTo(user);
  }

}
