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
package io.camunda.operate.qa.backup;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;

import io.camunda.operate.entities.OperationType;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.rest.StatefulRestTemplate;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.rest.dto.SequenceFlowDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import java.net.URI;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class OperateAPICaller {

  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";

  @Autowired private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;

  private StatefulRestTemplate restTemplate;

  public StatefulRestTemplate createRestTemplate(TestContext testContext) {
    restTemplate =
        statefulRestTemplateFactory.apply(
            testContext.getExternalOperateHost(), testContext.getExternalOperatePort());
    restTemplate.loginWhenNeeded(USERNAME, PASSWORD);
    return restTemplate;
  }

  public ProcessGroupDto[] getGroupedProcesses() {
    return restTemplate.getForObject(
        restTemplate.getURL("/api/processes/grouped"), ProcessGroupDto[].class);
  }

  public ListViewResponseDto getProcessInstances() {
    ListViewRequestDto processInstanceQueryDto = createGetAllProcessInstancesRequest();
    return restTemplate.postForObject(
        restTemplate.getURL("/api/process-instances"),
        processInstanceQueryDto,
        ListViewResponseDto.class);
  }

  public ListViewResponseDto getIncidentProcessInstances() {
    ListViewRequestDto processInstanceQueryDto =
        createGetAllProcessInstancesRequest(
            q ->
                q.setIncidents(true)
                    .setActive(false)
                    .setRunning(true)
                    .setCompleted(false)
                    .setCanceled(false)
                    .setFinished(false));
    return restTemplate.postForObject(
        restTemplate.getURL("/api/process-instances"),
        processInstanceQueryDto,
        ListViewResponseDto.class);
  }

  public SequenceFlowDto[] getSequenceFlows(String processInstanceId) {
    return restTemplate.getForObject(
        restTemplate.getURL("/api/process-instances/" + processInstanceId + "/sequence-flows"),
        SequenceFlowDto[].class);
  }

  public TakeBackupResponseDto backup(Long backupId) {
    TakeBackupRequestDto takeBackupRequest = new TakeBackupRequestDto().setBackupId(backupId);
    return restTemplate.postForObject(
        restTemplate.getURL("/actuator/backups"), takeBackupRequest, TakeBackupResponseDto.class);
  }

  public GetBackupStateResponseDto getBackupState(Long backupId) {
    return restTemplate.getForObject(
        restTemplate.getURL("/actuator/backups/" + backupId), GetBackupStateResponseDto.class);
  }

  boolean createOperation(Long processInstanceKey, OperationType operationType) {
    Map<String, Object> operationRequest =
        CollectionUtil.asMap("operationType", operationType.name());
    final URI url =
        restTemplate.getURL("/api/process-instances/" + processInstanceKey + "/operation");
    ResponseEntity<Map> operationResponse =
        restTemplate.postForEntity(url, operationRequest, Map.class);
    return operationResponse.getStatusCode().equals(HttpStatus.OK)
        && operationResponse.getBody().get(BatchOperationTemplate.ID) != null;
  }
}
