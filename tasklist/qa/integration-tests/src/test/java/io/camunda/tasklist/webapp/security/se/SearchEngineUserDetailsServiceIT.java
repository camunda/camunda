/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.se;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.camunda.tasklist.util.DatabaseTestExtension;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import io.camunda.webapps.schema.descriptors.index.TasklistUserIndex;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * This test tests that: 1. If we configure custom username and password, this user is added to
 * Elasticsearch 2. If we adjust firstname and lastname in Elasticsearch, this values are returned
 * by UserDetailsService
 */
@SpringBootTest(
    classes = {
      TestElasticsearchSchemaManager.class,
      TestApplication.class,
      SearchEngineHealthIndicator.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
    },
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + ".userId = user1",
      TasklistProperties.PREFIX + ".password = psw1",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SearchEngineUserDetailsServiceIT extends TasklistIntegrationTest {

  private static final String TEST_USERNAME = "user1";
  private static final String TEST_PASSWORD = "psw1";
  private static final String TEST_FIRSTNAME = "Quentin";
  private static final String TEST_LASTNAME = "Tarantino ";

  @RegisterExtension @Autowired public DatabaseTestExtension databaseTestExtension;

  @Autowired private SearchEngineUserDetailsService userDetailsService;

  @Autowired private TasklistUserIndex userIndex;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private NoSqlHelper noSqlHelper;

  @BeforeEach
  public void setUp() {
    super.before();
    databaseTestExtension.refreshTasklistIndices();
  }

  @AfterEach
  public void deleteUser() throws IOException {
    deleteById(TEST_USERNAME);
  }

  @Test
  public void testCustomUserIsAdded() {
    // when
    userDetailsService.initializeUsers();
    databaseTestExtension.refreshTasklistIndices();

    // and
    updateUserRealName();
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofSeconds(1))
        .until(() -> userDetailsService.loadUserByUsername(TEST_USERNAME) != null);

    // then
    final UserDetails userDetails = userDetailsService.loadUserByUsername(TEST_USERNAME);
    assertThat(userDetails).isInstanceOf(CamundaUser.class);
    final CamundaUser testUser = (CamundaUser) userDetails;
    assertThat(testUser.getUsername()).isEqualTo(TEST_USERNAME);
    assertThat(passwordEncoder.matches(TEST_PASSWORD, testUser.getPassword())).isTrue();
    assertThat(testUser.getUserId()).isEqualTo("user1");
    assertThat(testUser.getDisplayName()).isEqualTo(TEST_FIRSTNAME + " " + TEST_LASTNAME);
    assertThat(testUser.getAuthorities())
        .isEqualTo(Set.of(new SimpleGrantedAuthority(Role.OWNER.name())));
  }

  private void updateUserRealName() {
    try {
      final Map<String, Object> jsonMap = new HashMap<>();
      jsonMap.put(
          TasklistUserIndex.DISPLAY_NAME, String.format("%s %s", TEST_FIRSTNAME, TEST_LASTNAME));
      noSqlHelper.update(userIndex.getFullQualifiedName(), TEST_USERNAME, jsonMap);
      databaseTestExtension.refreshTasklistIndices();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteById(final String id) throws IOException {
    noSqlHelper.delete(userIndex.getFullQualifiedName(), id);
  }
}
