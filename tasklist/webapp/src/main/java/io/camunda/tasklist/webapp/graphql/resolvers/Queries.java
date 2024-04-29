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
import graphql.kickstart.annotations.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.graphql.entity.FormDTO;
import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import io.camunda.tasklist.webapp.service.TaskService;
import io.camunda.tasklist.webapp.service.VariableService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@GraphQLQueryResolver
public class Queries {

  @GraphQLField
  @GraphQLNonNull
  public static UserDTO currentUser() {
    return getBean(UserReader.class).getCurrentUser();
  }

  @GraphQLField
  public static List<TaskDTO> tasks(final TaskQueryDTO query) {
    return getBean(TaskService.class).getTasks(query);
  }

  @GraphQLField
  @GraphQLNonNull
  public static TaskDTO task(final String id) {
    return getBean(TaskService.class).getTask(id);
  }

  @GraphQLField
  public static List<VariableDTO> variables(
      final String taskId, final List<String> variableNames, final DataFetchingEnvironment env) {
    return getBean(VariableService.class).getVariables(taskId, variableNames, getFieldNames(env));
  }

  /**
   * Variable id here can be either "scopeId-varName" for runtime variables or "taskId-varName" for
   * completed task variables
   *
   * @param id: variableId
   * @return
   */
  @GraphQLField
  @GraphQLNonNull
  public static VariableDTO variable(final String id, final DataFetchingEnvironment env) {
    return getBean(VariableService.class).getVariable(id, getFieldNames(env));
  }

  @GraphQLField
  public static FormDTO form(final String id, final String processDefinitionId) {
    return FormDTO.createFrom(getBean(FormStore.class).getForm(id, processDefinitionId, null));
  }

  @GraphQLField
  public static List<ProcessDTO> processes(final String search) {
    return getBean(ProcessStore.class)
        .getProcesses(
            search,
            getBean(IdentityAuthorizationService.class).getProcessDefinitionsFromAuthorization(),
            DEFAULT_TENANT_IDENTIFIER,
            null)
        .stream()
        .map(ProcessDTO::createFrom)
        .collect(Collectors.toList());
  }

  private static Set<String> getFieldNames(final DataFetchingEnvironment env) {
    return env.getSelectionSet().getFields().stream()
        .map(SelectedField::getName)
        .collect(Collectors.toSet());
  }
}
