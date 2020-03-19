/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.security.es;

import java.util.Arrays;
import java.util.List;
import org.camunda.operate.entities.UserEntity;
import org.camunda.operate.es.schema.indices.UserIndex;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.webapp.rest.exception.NotFoundException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserStorageIT extends OperateIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(UserStorageIT.class);

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private UserStorage userStorage;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private UserIndex userIndex;

  protected List<String> allUsernames() {
    return Arrays.asList("test-user,act,demo".split(","));
  }

  protected void deleteAllUsers() {
    allUsernames().stream().forEach(this::deleteById);
  }

  public void deleteById(String id) {
    try {
      DeleteRequest request = new DeleteRequest(userIndex.getIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, id);
      esClient.delete(request, RequestOptions.DEFAULT);
    } catch (Exception ex) {
      logger.error("Could not delete user by id {}", id, ex);
    }
  }

  protected void assertAllUsersAreDeleted() {
    elasticsearchTestRule.refreshOperateESIndices();
    allUsernames().forEach(username -> {
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
    UserEntity user = UserEntity.from("test-user", "test-password", "USER");
    userStorage.create(user);
    elasticsearchTestRule.refreshOperateESIndices();
    assertThat(userStorage.getByName("test-user")).isEqualTo(user);
  }

}
