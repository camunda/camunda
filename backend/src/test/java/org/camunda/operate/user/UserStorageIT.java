/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.camunda.operate.entities.UserEntity;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserStorageIT extends OperateIntegrationTest{

  @Autowired
  UserStorage userStorage;
  
  protected void deleteAllUsers() {
    Arrays.asList("test-user,act,demo".split(",")).stream().forEach(userStorage::deleteUserById);
  }
  
  @Before
  public void setUp() {
    assertThat(userStorage).isNotNull();
    deleteAllUsers();
    assertThat(userStorage.usersExists()).isFalse();
  }
  
  @After
  public void tearDown() {
    deleteAllUsers();
    assertThat(userStorage.usersExists()).isFalse();
  }

  @Test
  public void testCreateUserAndGetByName() {
    assertThatThrownBy(() -> userStorage.getUserByName("test-user")).isInstanceOf(NotFoundException.class);
    UserEntity user = UserEntity.from("test-user","test-password","USER");
    userStorage.createUser(user);
    assertThat(userStorage.getUserByName("test-user")).isEqualTo(user);
  }

  @Test
  public void testSaveUser() {
    UserEntity user = UserEntity.from("test-user","test-password","USER");
    userStorage.createUser(user);
    assertThat(userStorage.getUserByName("test-user")).isEqualTo(user);
    user.setPassword("test-another-password");
    userStorage.saveUser(user);
    assertThat(userStorage.getUserByName("test-user").getPassword()).isEqualTo("test-another-password");
  }

}
