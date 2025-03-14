/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.management.IndicesCheck;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.qa.util.TestElasticsearchSchemaManager;
import io.camunda.operate.qa.util.TestSchemaManager;
import io.camunda.operate.util.IndexPrefixHolder;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplication.class,
      OAuth2WebConfigurer.class,
      TestElasticsearchSchemaManager.class
    },
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      "spring.profiles.active=consolidated-auth,test"
    })
public class ProbesTestIT {

  @Autowired private OperateProperties operateProperties;

  @Autowired private TestSchemaManager schemaManager;

  @Autowired private IndicesCheck probes;

  @Autowired private IndexPrefixHolder indexPrefixHolder;

  @MockBean private UserService userService;

  @Before
  public void before() {
    when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId("testuser"));
    schemaManager.setIndexPrefix(indexPrefixHolder.createNewIndexPrefix());
  }

  @After
  public void after() {
    schemaManager.deleteSchemaQuietly();
    schemaManager.setDefaultIndexPrefix();
  }

  @Test
  public void testIsReady() {
    assertThat(probes.indicesArePresent()).isFalse();
    schemaManager.setCreateSchema(true);
    schemaManager.createSchema();
    assertThat(probes.indicesArePresent()).isTrue();
  }

  @Test
  public void testIsNotReady() {
    schemaManager.setCreateSchema(false);
    assertThat(probes.indicesArePresent()).isFalse();
  }
}
