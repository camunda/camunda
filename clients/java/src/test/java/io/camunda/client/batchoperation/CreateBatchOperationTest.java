/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.protocol.rest.BatchOperationCreatedResult;
import io.camunda.client.protocol.rest.MigrateProcessInstanceMappingInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceFilter;
import io.camunda.client.protocol.rest.ProcessInstanceMigrationBatchOperationPlan;
import io.camunda.client.protocol.rest.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.client.protocol.rest.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.List;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public final class CreateBatchOperationTest extends ClientRestTest {

  @Test
  public void shouldSendProcessInstanceCancelCommandEmptyFilter() {
    // given
    gatewayService.onCancelProcessInstancesRequest(
        Instancio.create(BatchOperationCreatedResult.class).batchOperationKey("2"));

    // when
    client
        .newCreateBatchOperationCommand()
        .processInstanceCancel()
        .filter(filter -> {})
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(request.getUrl()).isEqualTo("/v2/process-instances/cancellation");

    final ProcessInstanceFilter filter = gatewayService.getLastRequest(ProcessInstanceFilter.class);
    assertThat(filter).isNotNull();
  }

  @Test
  public void shouldSendProcessInstanceCancelCommandWithFilter() {
    // given
    gatewayService.onCancelProcessInstancesRequest(
        Instancio.create(BatchOperationCreatedResult.class).batchOperationKey("2"));

    // when
    client
        .newCreateBatchOperationCommand()
        .processInstanceCancel()
        .filter(filter -> filter.processDefinitionId("test-01"))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(request.getUrl()).isEqualTo("/v2/process-instances/cancellation");

    final ProcessInstanceFilter filter = gatewayService.getLastRequest(ProcessInstanceFilter.class);
    assertThat(filter.getProcessDefinitionId().get$Eq()).isEqualTo("test-01");
  }

  @Test
  public void shouldSendProcessInstanceMigrationCommand() {
    // given
    gatewayService.onMigrateProcessInstancesRequest(
        Instancio.create(BatchOperationCreatedResult.class).batchOperationKey("2"));

    // when
    client
        .newCreateBatchOperationCommand()
        .migrateProcessInstance()
        .addMappingInstruction("source", "target")
        .addMappingInstruction("source2", "target2")
        .targetProcessDefinitionKey(1L)
        .filter(filter -> filter.processDefinitionId("test-01"))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(request.getUrl()).isEqualTo("/v2/process-instances/migration");

    final ProcessInstanceMigrationBatchOperationRequest lastRequest =
        gatewayService.getLastRequest(ProcessInstanceMigrationBatchOperationRequest.class);
    assertThat(lastRequest.getFilter().getProcessDefinitionId().get$Eq()).isEqualTo("test-01");
    final ProcessInstanceMigrationBatchOperationPlan migrationPlan = lastRequest.getMigrationPlan();
    assertThat(migrationPlan).isNotNull();
    assertThat(migrationPlan.getTargetProcessDefinitionKey()).isEqualTo("1");
    final List<MigrateProcessInstanceMappingInstruction> mappingInstructions =
        migrationPlan.getMappingInstructions();
    assertThat(mappingInstructions).hasSize(2);
    assertThat(mappingInstructions.get(0).getSourceElementId()).isEqualTo("source");
    assertThat(mappingInstructions.get(0).getTargetElementId()).isEqualTo("target");
    assertThat(mappingInstructions.get(1).getSourceElementId()).isEqualTo("source2");
    assertThat(mappingInstructions.get(1).getTargetElementId()).isEqualTo("target2");
  }

  @Test
  public void shouldSendProcessInstanceModificationCommand() {
    // given
    gatewayService.onModifyProcessInstances(
        Instancio.create(BatchOperationCreatedResult.class).batchOperationKey("2"));

    // when
    client
        .newCreateBatchOperationCommand()
        .modifyProcessInstance()
        .addMoveInstruction("source", "target")
        .addMoveInstruction("source2", "target2")
        .filter(filter -> filter.processDefinitionId("test-01"))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    assertThat(request.getUrl()).isEqualTo("/v2/process-instances/modification");

    final ProcessInstanceModificationBatchOperationRequest lastRequest =
        gatewayService.getLastRequest(ProcessInstanceModificationBatchOperationRequest.class);
    assertThat(lastRequest.getFilter().getProcessDefinitionId().get$Eq()).isEqualTo("test-01");
    assertThat(lastRequest.getMoveInstructions()).hasSize(2);
    assertThat(lastRequest.getMoveInstructions().get(0).getSourceElementId()).isEqualTo("source");
    assertThat(lastRequest.getMoveInstructions().get(0).getTargetElementId()).isEqualTo("target");
    assertThat(lastRequest.getMoveInstructions().get(1).getSourceElementId()).isEqualTo("source2");
    assertThat(lastRequest.getMoveInstructions().get(1).getTargetElementId()).isEqualTo("target2");
  }
}
