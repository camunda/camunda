/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.es;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.security.auth.OperateUserDetailsService;
import io.camunda.operate.webapp.security.auth.Role;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * This test tests that: 1. If we configure custom username and password, this user is added to
 * Elasticsearch 2. If we adjust firstname and lastname in Elasticsearch, these values are returned
 * by UserDetailsService
 */
@SpringBootTest(
    classes = {TestApplication.class},
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
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @Autowired private OperateUserDetailsService userDetailsService;
  @Autowired private TestSearchRepository testSearchRepository;
  @Autowired private UserIndex userIndex;
  @Autowired private PasswordEncoder passwordEncoder;

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
    // when
    userDetailsService.initializeUsers();
    searchTestRule.refreshOperateSearchIndices();

    // and
    updateUserRealName();

    // then
    final CamundaUser user = userDetailsService.loadUserByUsername(TEST_USER_ID);
    assertThat(user.getUsername()).isEqualTo(TEST_USER_ID);
    assertThat(passwordEncoder.matches(TEST_PASSWORD, user.getPassword())).isTrue();
    assertThat(user.getDisplayName()).isEqualTo(TEST_USER_DISPLAYNAME);
    assertThat(user.getRoles()).isEqualTo(List.of(Role.OWNER.toString()));
  }

  private void updateUserRealName() {
    try {
      final Map<String, Object> jsonMap = Map.of(UserIndex.DISPLAY_NAME, TEST_USER_DISPLAYNAME);
      testSearchRepository.update(userIndex.getFullQualifiedName(), TEST_USER_ID, jsonMap);
      searchTestRule.refreshOperateSearchIndices();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteById(final String id) {
    try {
      testSearchRepository.deleteById(userIndex.getFullQualifiedName(), id);
    } catch (final IOException ex) {
      // noop
    }
  }
}
