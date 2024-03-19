/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.TestSchemaManager;
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
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {"spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"})
@WithMockUser(username = AuthorizationIT.USER)
public class AuthorizationIT {

  protected static final String USER = "calculon";

  @MockBean UserService<? extends Authentication> userService;

  @Autowired private ProcessInstanceRestService processInstanceRestService;

  @Autowired private TestSchemaManager testSchemaManager;

  @Autowired private OperateProperties operateProperties;

  @Before
  public void before() {
    operateProperties
        .getElasticsearch()
        .setIndexPrefix("test-probes-" + TestUtil.createRandomString(5));
    testSchemaManager.createSchema();
  }

  @After
  public void after() {
    testSchemaManager.deleteSchemaQuietly();
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
    processInstanceRestService.operation(
        "23",
        new CreateOperationRequestDto().setOperationType(OperationType.DELETE_PROCESS_INSTANCE));
    // then throw AccessDeniedException
  }

  @Test
  public void testWritePermissionsForBatchOperation() {
    // given
    userHasPermission(Permission.WRITE);
    // when
    final BatchOperationEntity batchOperationEntity =
        processInstanceRestService.createBatchOperation(
            new CreateBatchOperationRequestDto()
                .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)
                .setQuery(new ListViewQueryDto().setCompleted(true).setFinished(true)));
    // then
    assertThat(batchOperationEntity).isNotNull();
  }

  @Test
  public void testWritePermissionsForSingleOperation() {
    // given
    userHasPermission(Permission.WRITE);

    final Exception e =
        assertThrows(
            OperateRuntimeException.class,
            () -> {
              // when
              processInstanceRestService.operation(
                  "23",
                  new CreateOperationRequestDto()
                      .setOperationType(OperationType.DELETE_PROCESS_INSTANCE));
            });
    // then
    final Throwable cause = e.getCause();
    assertThat(cause).isInstanceOf(NotFoundException.class);
    final String errorMsg =
        DatabaseInfo.isOpensearch()
            ? "Process instances [23] doesn't exists."
            : "Could not find process instance with id '23'.";
    assertThat(cause.getMessage()).isEqualTo(errorMsg);
  }

  private void userHasPermission(Permission permission) {
    when(userService.getCurrentUser())
        .thenReturn(new UserDto().setUserId(USER).setPermissions(List.of(permission)));
  }
}
