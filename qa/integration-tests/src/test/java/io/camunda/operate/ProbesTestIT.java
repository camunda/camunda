/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.management.ElsIndicesCheck;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.TestElasticsearchSchemaManager;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import io.camunda.operate.webapp.security.UserService;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class, OAuth2WebConfigurer.class,
        TestElasticsearchSchemaManager.class},
    properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"}
)
public class ProbesTestIT{

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private TestElasticsearchSchemaManager schemaManager;

  @Autowired
  private ElsIndicesCheck probes;

  @MockBean
  private UserService userService;

  @Before
  public void before() {
    when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId("testuser"));
    operateProperties.getElasticsearch().setIndexPrefix("test-probes-"+TestUtil.createRandomString(5));
  }

  @After
  public void after() {
    schemaManager.deleteSchemaQuietly();
    operateProperties.getElasticsearch().setDefaultIndexPrefix();
  }

  @Test
  public void testIsReady() {
    assertThat(probes.indicesArePresent()).isFalse();
    enableCreateSchema(true);
    schemaManager.createSchema();
    assertThat(probes.indicesArePresent()).isTrue();
  }

  @Test
  public void testIsNotReady() {
    enableCreateSchema(false);
    assertThat(probes.indicesArePresent()).isFalse();
  }

  protected void enableCreateSchema(boolean createSchema) {
    operateProperties.getElasticsearch().setCreateSchema(createSchema);
  }
}
