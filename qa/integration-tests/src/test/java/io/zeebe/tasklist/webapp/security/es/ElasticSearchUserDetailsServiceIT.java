/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.es;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import io.zeebe.tasklist.es.schema.indices.UserIndex;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.ElasticsearchTestRule;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.util.TasklistIntegrationTest;
import io.zeebe.tasklist.util.TestApplication;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test tests that:
 * 1. If we configure custom username and password, this user is added to Elasticsearch
 * 2. If we adjust firstname and lastname in Elasticsearch, this values are returned by UserDetailsService
 */
@SpringBootTest(
    classes = { TestApplication.class},
    properties = {TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
        TasklistProperties.PREFIX + ".username = user1",
        TasklistProperties.PREFIX + ".password = psw1",
        "graphql.servlet.websocket.enabled=false"})
public class ElasticSearchUserDetailsServiceIT extends TasklistIntegrationTest {

  private static final String TEST_USERNAME = "user1";
  private static final String TEST_PASSWORD = "psw1";
  private static final String TEST_FIRSTNAME = "Quentin";
  private static final String TEST_LASTNAME = "Tarantino ";

  @Autowired
  private ElasticSearchUserDetailsService userDetailsService;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private UserIndex userIndex;

  @Autowired
  private PasswordEncoder passwordEncoder;
  
  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Before
  public void setUp() {
    elasticsearchTestRule.refreshTasklistESIndices();
  }

  @After
  public void deleteUser() {
    deleteById(TEST_USERNAME);
  }

  @Test
  public void testCustomUserIsAdded() {
    //when
    userDetailsService.initializeUsers();
    elasticsearchTestRule.refreshTasklistESIndices();

    //and
    updateUserRealName();

    //then
    UserDetails userDetails = userDetailsService.loadUserByUsername(TEST_USERNAME);
    assertThat(userDetails).isInstanceOf(User.class);
    User testUser = (User)userDetails;
    assertThat(testUser.getUsername()).isEqualTo(TEST_USERNAME);
    assertThat(passwordEncoder.matches(TEST_PASSWORD, testUser.getPassword())).isTrue();
    assertThat(testUser.getFirstname()).isEqualTo(TEST_FIRSTNAME);
    assertThat(testUser.getLastname()).isEqualTo(TEST_LASTNAME);
  }

  private void updateUserRealName() {
    try {
      Map<String, Object> jsonMap = new HashMap<>();
      jsonMap.put(UserIndex.FIRSTNAME, TEST_FIRSTNAME);
      jsonMap.put(UserIndex.LASTNAME, TEST_LASTNAME);
      UpdateRequest request = new UpdateRequest(userIndex.getIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, TEST_USERNAME)
          .doc(jsonMap);
      esClient.update(request, RequestOptions.DEFAULT);
      elasticsearchTestRule.refreshTasklistESIndices();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteById(String id) {
    try {
      DeleteRequest request = new DeleteRequest(userIndex.getIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, id);
      esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      //
    }
  }

}
