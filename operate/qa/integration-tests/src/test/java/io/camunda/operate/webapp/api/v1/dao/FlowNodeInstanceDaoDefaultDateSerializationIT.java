/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false",
      OperateProperties.PREFIX + ".rfc3339ApiDateFormat = false"
    })
public class FlowNodeInstanceDaoDefaultDateSerializationIT extends OperateSearchAbstractIT {
  private final String firstNodeStartDate = "2024-02-15T22:40:10.834+0000";
  private final String secondNodeStartDate = "2024-02-15T22:41:10.834+0000";
  private final String thirdNodeStartDate = "2024-01-15T22:40:10.834+0000";
  private final String endDate = "2024-02-15T22:41:10.834+0000";
  @Autowired private FlowNodeInstanceDao dao;
  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceIndex;
  @MockBean private ProcessCache processCache;
  @Autowired private OperateDateTimeFormatter dateTimeFormatter;

  @Override
  public void runAdditionalBeforeAllSetup() throws Exception {

    final String indexName = flowNodeInstanceIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685256L)
            .setProcessInstanceKey(2251799813685253L)
            .setProcessDefinitionKey(2251799813685249L)
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(firstNodeStartDate))
            .setEndDate(dateTimeFormatter.parseGeneralDateTime(endDate))
            .setFlowNodeId("start")
            .setType(FlowNodeType.START_EVENT)
            .setState(FlowNodeState.COMPLETED)
            .setIncident(false)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685258L)
            .setProcessInstanceKey(2251799813685253L)
            .setProcessDefinitionKey(2251799813685249L)
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(secondNodeStartDate))
            .setEndDate(null)
            .setFlowNodeId("taskA")
            .setType(FlowNodeType.SERVICE_TASK)
            .setIncidentKey(2251799813685264L)
            .setState(FlowNodeState.ACTIVE)
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685260L)
            .setProcessInstanceKey(2251799813685283L)
            .setProcessDefinitionKey(2251799813685299L)
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(thirdNodeStartDate))
            .setEndDate(null)
            .setFlowNodeId("taskB")
            .setType(FlowNodeType.SERVICE_TASK)
            .setIncidentKey(2251799813685268L)
            .setState(FlowNodeState.ACTIVE)
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-flow*");
  }

  @Override
  public void runAdditionalBeforeEachSetup() {
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("start"), eq(null)))
        .thenReturn("start");
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("taskA"), eq(null)))
        .thenReturn("task A");
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("taskB"), eq(null)))
        .thenReturn("task B");
  }

  @Test
  public void shouldFilterByStartDate() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setStartDate(firstNodeStartDate)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDate);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
  }

  @Test
  public void shouldFilterByStartDateWithDateMath() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setStartDate(firstNodeStartDate + "||/d")));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(2L);

    FlowNodeInstance checkFlowNode =
        flowNodeInstanceResults.getItems().stream()
            .filter(item -> "START_EVENT".equals(item.getType()))
            .findFirst()
            .orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName", "startDate", "endDate")
        .containsExactly("start", "start", firstNodeStartDate, endDate);

    checkFlowNode =
        flowNodeInstanceResults.getItems().stream()
            .filter(item -> "SERVICE_TASK".equals(item.getType()))
            .findFirst()
            .orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName", "startDate", "endDate")
        .containsExactly("taskA", "task A", secondNodeStartDate, null);
  }

  @Test
  public void shouldFilterByEndDate() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>().setFilter(new FlowNodeInstance().setEndDate(endDate)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDate);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
  }

  @Test
  public void shouldFilterByEndDateWithDateMath() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setEndDate(endDate + "||/d")));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDate);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
  }

  @Test
  public void shouldFormatDatesWhenSearchByKey() {
    final FlowNodeInstance flowNodeInstance = dao.byKey(2251799813685256L);

    assertThat(flowNodeInstance.getStartDate()).isEqualTo(firstNodeStartDate);
    assertThat(flowNodeInstance.getEndDate()).isEqualTo(endDate);
    assertThat(flowNodeInstance.getKey()).isEqualTo(2251799813685256L);
  }
}
