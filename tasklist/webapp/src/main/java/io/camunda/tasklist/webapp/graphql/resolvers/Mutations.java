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
package io.camunda.tasklist.webapp.graphql.resolvers;

import static io.camunda.tasklist.util.SpringContextHolder.getBean;
import static io.camunda.zeebe.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.kickstart.annotations.GraphQLMutationResolver;
import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.enums.DeletionStatus;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.ProcessInstanceStore;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.service.ProcessService;
import io.camunda.tasklist.webapp.service.TaskService;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@GraphQLMutationResolver
public class Mutations {

  private static final Logger LOGGER = LoggerFactory.getLogger(Mutations.class);
  private static final String ZEEBE_USER_TASK_OPERATIONS_NOT_SUPPORTED =
      "This operation is not supported using Tasklist graphql API. Please use the latest REST API. For more information, refer to the documentation: %s";

  @GraphQLField
  @GraphQLNonNull
  public static TaskDTO completeTask(final String taskId, final List<VariableInputDTO> variables) {
    return delegate(
        () -> {
          checkTaskImplementation(taskId);
          return getBean(TaskService.class).completeTask(taskId, variables, false);
        });
  }

  @GraphQLField
  @GraphQLNonNull
  public static TaskDTO claimTask(
      final String taskId, final String assignee, final Boolean allowOverrideAssignment) {
    return delegate(
        () -> {
          checkTaskImplementation(taskId);
          return getBean(TaskService.class).assignTask(taskId, assignee, allowOverrideAssignment);
        });
  }

  @GraphQLField
  @GraphQLNonNull
  public static TaskDTO unclaimTask(final String taskId) {
    return delegate(
        () -> {
          checkTaskImplementation(taskId);
          return getBean(TaskService.class).unassignTask(taskId);
        });
  }

  @GraphQLField
  @GraphQLNonNull
  public static Boolean deleteProcessInstance(final String processInstanceId) {
    return delegate(
        () ->
            DeletionStatus.DELETED.equals(
                getBean(ProcessInstanceStore.class).deleteProcessInstance(processInstanceId)));
  }

  @GraphQLField
  @GraphQLNonNull
  public static ProcessInstanceDTO startProcess(final String processDefinitionId) {
    return delegate(
        () ->
            getBean(ProcessService.class)
                .startProcessInstance(processDefinitionId, DEFAULT_TENANT_IDENTIFIER));
  }

  private static <T> T delegate(final Supplier<T> supplier) {
    return getBean(MutationAuthorizationDelegate.class).call(supplier);
  }

  private static void checkTaskImplementation(final String taskId) {
    final var task = getBean(TaskService.class).getTask(taskId);
    if (task.getImplementation() != TaskImplementation.JOB_WORKER) {
      LOGGER.warn(
          "GraphQL API is used for task with id={} implementation={}",
          task.getId(),
          task.getImplementation());
      throw new InvalidRequestException(
          String.format(
              ZEEBE_USER_TASK_OPERATIONS_NOT_SUPPORTED,
              getBean(TasklistProperties.class).getDocumentation().getApiMigrationDocsUrl()));
    }
  }

  @Component
  public static class MutationAuthorizationDelegate {
    @PreAuthorize("hasPermission('write')")
    public <T> T call(final Supplier<T> supplier) {
      return supplier.get();
    }
  }
}
