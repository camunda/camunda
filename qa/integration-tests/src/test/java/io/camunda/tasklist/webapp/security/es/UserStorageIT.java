/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.es;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.ElasticsearchTestRule;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "graphql.servlet.websocket.enabled=false"
    })
public class UserStorageIT extends TasklistIntegrationTest {

  @Rule public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired private ElasticsearchUserDetailsService userDetailsService;

  @Autowired private UserStorage userStorage;

  @Test
  public void usersByUsernamesShouldBeInSameOrder() {
    // given
    final var usernames = new ArrayList<>(List.of("demo", "jane", "joe"));
    usernames.forEach(
        username ->
            userDetailsService.addUserWith(
                username, username, ElasticsearchUserDetailsService.USER_ROLE));
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    // when ( getting request of random ordered usernames )
    Collections.shuffle(usernames);

    // then
    assertThat(userStorage.getUsersByUsernames(usernames).stream().map(UserEntity::getUsername))
        .isEqualTo(usernames);
  }
}
