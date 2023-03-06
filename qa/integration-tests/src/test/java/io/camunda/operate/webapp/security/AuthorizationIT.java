/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.TestElasticsearchSchemaManager;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { TestApplication.class },
    properties = {"spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER" })
@WithMockUser(username = AuthorizationIT.USER)
public class AuthorizationIT {

  protected static final String USER = "calculon";

  @MockBean UserService<? extends Authentication> userService;

  @Autowired
  private ProcessInstanceRestService processInstanceRestService;

  @Autowired
  private TestElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  private OperateProperties operateProperties;

  @Before
  public void before() {
    operateProperties.getElasticsearch().setIndexPrefix("test-probes-"+ TestUtil.createRandomString(5));
    elasticsearchSchemaManager.createSchema();
  }

  @After
  public void after() {
    elasticsearchSchemaManager.deleteSchemaQuietly();
    operateProperties.getElasticsearch().setDefaultIndexPrefix();
  }

  @Test(expected = AccessDeniedException.class)
  public void testNoWritePermissionsForBatchOperation() {
    // given
    userHasPermission(Permission.READ);
    // when
    processInstanceRestService.createBatchOperation(new CreateBatchOperationRequestDto());
    // then throw AccessDeniedException
  }

  @Test(expected = AccessDeniedException.class)
  public void testNoWritePermissionsForSingleOperation() {
    // given
    userHasPermission(Permission.READ);
    // when
    processInstanceRestService.operation("23",new CreateOperationRequestDto()
        .setOperationType(OperationType.DELETE_PROCESS_INSTANCE));
    // then throw AccessDeniedException
  }

  @Test
  public void testWritePermissionsForBatchOperation() {
    //given
    userHasPermission(Permission.WRITE);
    // when
    BatchOperationEntity batchOperationEntity = processInstanceRestService.createBatchOperation(new CreateBatchOperationRequestDto().setOperationType(
        OperationType.DELETE_PROCESS_INSTANCE
    ).setQuery(new ListViewQueryDto()
        .setCompleted(true)
        .setFinished(true)));
    // then
    assertThat(batchOperationEntity).isNotNull();
  }

  @Test
  public void testWritePermissionsForSingleOperation() {
    // given
    userHasPermission(Permission.WRITE);
    // when
    try {
      processInstanceRestService.operation("23", new CreateOperationRequestDto().setOperationType(
          OperationType.DELETE_PROCESS_INSTANCE
      ));
      // then
    } catch(OperateRuntimeException e) {
      Throwable cause = e.getCause();
        assertThat(cause).isInstanceOf(NotFoundException.class);
        assertThat(cause.getMessage()).isEqualTo("Could not find process instance with id '23'.");
    }
  }

  private void userHasPermission(Permission permission) {
    when(userService.getCurrentUser()).thenReturn(
        new UserDto()
            .setUserId(USER)
            .setPermissions(List.of(permission)));
  }
}
