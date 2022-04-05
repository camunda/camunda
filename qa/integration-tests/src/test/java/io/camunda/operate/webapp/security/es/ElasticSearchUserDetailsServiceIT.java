/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.es;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.TestApplication;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test tests that:
 * 1. If we configure custom username and password, this user is added to Elasticsearch
 * 2. If we adjust firstname and lastname in Elasticsearch, these values are returned by UserDetailsService
 */
@SpringBootTest(
    classes = { TestApplication.class},
    properties = {
        OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        OperateProperties.PREFIX + ".userId = " + ElasticSearchUserDetailsServiceIT.TEST_USER_ID,
        OperateProperties.PREFIX + ".displayName = User 1",
        OperateProperties.PREFIX + ".password = " + ElasticSearchUserDetailsServiceIT.TEST_PASSWORD
    })
public class ElasticSearchUserDetailsServiceIT extends OperateIntegrationTest {

  public static final String TEST_USER_ID = "user1";
  public static final String TEST_USER_DISPLAYNAME = "Quentin Tarantino";
  public static final String TEST_PASSWORD = "psw1";

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
    elasticsearchTestRule.refreshOperateESIndices();
  }

  @After
  public void deleteUser() {
    deleteById(TEST_USER_ID);
  }

  @Test
  public void testCustomUserIsAdded() {
    //when
    userDetailsService.initializeUsers();
    elasticsearchTestRule.refreshOperateESIndices();

    //and
    updateUserRealName();

    //then
    User user = userDetailsService.loadUserByUsername(TEST_USER_ID);
    assertThat(user.getUsername()).isEqualTo(TEST_USER_ID);
    assertThat(passwordEncoder.matches(TEST_PASSWORD, user.getPassword())).isTrue();
    assertThat(user.getDisplayName()).isEqualTo(TEST_USER_DISPLAYNAME);
  }

  private void updateUserRealName() {
    try {
      Map<String, Object> jsonMap = new HashMap<>();
      jsonMap.put(UserIndex.DISPLAY_NAME, TEST_USER_DISPLAYNAME);
      UpdateRequest request = new UpdateRequest().index(userIndex.getFullQualifiedName()).id(
              TEST_USER_ID)
          .doc(jsonMap);
      esClient.update(request, RequestOptions.DEFAULT);
      elasticsearchTestRule.refreshOperateESIndices();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteById(String id) {
    try {
      DeleteRequest request = new DeleteRequest().index(userIndex.getFullQualifiedName()).id(id);
      esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      //
    }
  }

}
