/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.es;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.camunda.tasklist.util.ElasticsearchTestRule;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.webapp.security.Role;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      TestElasticsearchSchemaManager.class,
      TestApplication.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
      RetryElasticsearchClient.class,
    },
    properties = {"graphql.servlet.websocket.enabled=false"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserStorageIT extends TasklistIntegrationTest {

  @Rule public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired private ElasticsearchUserDetailsService userDetailsService;

  @Autowired private UserStorage userStorage;

  @Test
  public void usersByUsernamesShouldBeInSameOrder() {
    // given
    final var userIds = new ArrayList<>(List.of("demo", "jane", "joe"));
    userIds.forEach(
        userId ->
            userDetailsService.addUserWith(userId, userId, userId, List.of(Role.OPERATOR.name())));
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    // when ( getting request of random ordered usernames )
    Collections.shuffle(userIds);

    // then
    assertThat(userStorage.getUsersByUserIds(userIds).stream().map(UserEntity::getUserId))
        .isEqualTo(userIds);
  }
}
