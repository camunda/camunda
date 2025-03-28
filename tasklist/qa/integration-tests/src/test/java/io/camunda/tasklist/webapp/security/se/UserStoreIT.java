/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.se;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.camunda.tasklist.util.*;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import io.camunda.tasklist.webapp.security.se.store.UserStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(
    classes = {
      TestElasticsearchSchemaManager.class,
      TestApplication.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
    },
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"
    },
    webEnvironment = WebEnvironment.RANDOM_PORT)
public class UserStoreIT extends TasklistIntegrationTest {

  @RegisterExtension @Autowired public DatabaseTestExtension databaseTestExtension;

  @Autowired private SearchEngineUserDetailsService userDetailsService;

  @Autowired private UserStore userStore;

  @Test
  public void usersByUsernamesShouldBeInSameOrder() {
    // given
    final var userIds = new ArrayList<>(List.of("demo", "jane", "joe"));
    userIds.forEach(
        userId ->
            userDetailsService.addUserWith(userId, userId, userId, List.of(Role.OPERATOR.name())));
    databaseTestExtension.refreshIndexesInElasticsearch();
    // when ( getting request of random ordered usernames )
    Collections.shuffle(userIds);

    final var user0 = userStore.getByUserId(userIds.get(0));
    final var user1 = userStore.getByUserId(userIds.get(1));
    final var user2 = userStore.getByUserId(userIds.get(2));

    // then
    // To avoid flaky tests with the mapping when get multiple users by their ids
    assertThat(user0.getUserId()).isEqualTo(userIds.get(0));
    assertThat(user1.getUserId()).isEqualTo(userIds.get(1));
    assertThat(user2.getUserId()).isEqualTo(userIds.get(2));
  }
}
