/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.es;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.security.auth.OperateUserDetailsService;
import io.camunda.operate.webapp.security.auth.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.Map;

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
        OperateProperties.PREFIX + ".password = " + ElasticSearchUserDetailsServiceIT.TEST_PASSWORD,
        "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
public class ElasticSearchUserDetailsServiceIT extends OperateAbstractIT {

  public static final String TEST_USER_ID = "user1";
  public static final String TEST_USER_DISPLAYNAME = "Quentin Tarantino";
  public static final String TEST_PASSWORD = "psw1";

  @Autowired
  private OperateUserDetailsService userDetailsService;

  @Autowired
  private TestSearchRepository testSearchRepository;

  @Autowired
  private UserIndex userIndex;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Rule
  public SearchTestRule searchTestRule = new SearchTestRule();

  @Before
  public void setUp() {
    searchTestRule.refreshOperateSearchIndices();
  }

  @After
  public void deleteUser() {
    deleteById(TEST_USER_ID);
  }

  @Test
  public void testCustomUserIsAdded() {
    //when
    userDetailsService.initializeUsers();
    searchTestRule.refreshOperateSearchIndices();

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
      Map<String, Object> jsonMap = Map.of(UserIndex.DISPLAY_NAME, TEST_USER_DISPLAYNAME);
      testSearchRepository.update(userIndex.getFullQualifiedName(), TEST_USER_ID, jsonMap);
      searchTestRule.refreshOperateSearchIndices();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteById(String id) {
    try {
      testSearchRepository.deleteById(userIndex.getFullQualifiedName(), id);
    } catch (IOException ex) {}
  }

}
