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
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FlowNodeStatisticsDaoIT extends OperateSearchAbstractIT {

  private static final Long PROCESS_INSTANCE_KEY = 2251799813685251L;
  private static final Long PROCESS_DEFINITION_KEY = 2251799813685249L;
  @Autowired private FlowNodeStatisticsDao dao;
  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @Override
  public void runAdditionalBeforeAllSetup() throws Exception {
    final String indexName = flowNodeInstanceIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685254L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setFlowNodeId("start")
            .setType(FlowNodeType.START_EVENT)
            .setState(FlowNodeState.COMPLETED)
            .setIncident(false)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685256L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setFlowNodeId("ExclusiveGateway_05d8jf3")
            .setType(FlowNodeType.PARALLEL_GATEWAY)
            .setState(FlowNodeState.COMPLETED)
            .setIncident(false)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685258L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setFlowNodeId("taskD")
            .setType(FlowNodeType.SERVICE_TASK)
            .setState(FlowNodeState.ACTIVE)
            .setIncident(false)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685260L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setFlowNodeId("taskA")
            .setType(FlowNodeType.SERVICE_TASK)
            .setState(FlowNodeState.ACTIVE)
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-flow*");
  }

  @Test
  public void shouldReturnFlowNodeStatistics() {
    final List<FlowNodeStatistics> flowNodeStatistics =
        dao.getFlowNodeStatisticsForProcessInstance(PROCESS_INSTANCE_KEY);

    assertThat(flowNodeStatistics).hasSize(4);
    assertThat(
            flowNodeStatistics.stream()
                .filter(x -> x.getActivityId().equals("ExclusiveGateway_05d8jf3"))
                .findFirst()
                .orElseThrow()
                .getCompleted())
        .isEqualTo(1);
    assertThat(
            flowNodeStatistics.stream()
                .filter(x -> x.getActivityId().equals("start"))
                .findFirst()
                .orElseThrow()
                .getCompleted())
        .isEqualTo(1);
    assertThat(
            flowNodeStatistics.stream()
                .filter(x -> x.getActivityId().equals("taskA"))
                .findFirst()
                .orElseThrow()
                .getIncidents())
        .isEqualTo(1);
    assertThat(
            flowNodeStatistics.stream()
                .filter(x -> x.getActivityId().equals("taskD"))
                .findFirst()
                .orElseThrow()
                .getActive())
        .isEqualTo(1);
  }
}
