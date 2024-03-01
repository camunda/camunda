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

import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.entities.listview.ListViewJoinRelation;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false",
      OperateProperties.PREFIX + ".rfc3339ApiDateFormat = false"
    })
public class ProcessInstanceDaoDefaultDateSerializationIT extends OperateSearchAbstractIT {
  private final String firstInstanceStartDate = "2024-02-15T22:40:10.834+0000";
  private final String secondInstanceStartDate = "2024-02-15T22:41:10.834+0000";
  private final String thirdInstanceStartDate = "2024-01-15T22:40:10.834+0000";
  private final String endDate = "2024-02-15T22:42:10.834+0000";
  @Autowired private ProcessInstanceDao dao;
  @Autowired private ListViewTemplate processInstanceIndex;
  @Autowired private OperateDateTimeFormatter dateTimeFormatter;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685251")
            .setKey(2251799813685251L)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess-1")
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(firstInstanceStartDate))
            .setEndDate(dateTimeFormatter.parseGeneralDateTime(endDate))
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685251")
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID)
            .setProcessInstanceKey(2251799813685251L)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    processInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685252")
            .setKey(2251799813685252L)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess-2")
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(secondInstanceStartDate))
            .setEndDate(null)
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685252")
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID)
            .setProcessInstanceKey(2251799813685252L)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    processInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685253")
            .setKey(2251799813685253L)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess-3")
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(thirdInstanceStartDate))
            .setEndDate(null)
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685253")
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID)
            .setProcessInstanceKey(2251799813685253L)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    searchContainerManager.refreshIndices("*operate-list*");
  }

  @Test
  public void shouldFilterByStartDate() {
    final Results<ProcessInstance> processInstanceResults =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setStartDate(firstInstanceStartDate)));

    assertThat(processInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(processInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstInstanceStartDate);
    assertThat(processInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
    assertThat(processInstanceResults.getItems().get(0).getBpmnProcessId())
        .isEqualTo("demoProcess-1");
  }

  @Test
  public void shouldFilterByStartDateWithDateMath() {
    final Results<ProcessInstance> processInstanceResults =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setStartDate(firstInstanceStartDate + "||/d")));

    assertThat(processInstanceResults.getTotal()).isEqualTo(2L);

    ProcessInstance checkInstance =
        processInstanceResults.getItems().stream()
            .filter(item -> "demoProcess-1".equals(item.getBpmnProcessId()))
            .findFirst()
            .orElse(null);

    assertThat(checkInstance.getBpmnProcessId()).isEqualTo("demoProcess-1");
    assertThat(checkInstance.getStartDate()).isEqualTo(firstInstanceStartDate);
    assertThat(checkInstance.getEndDate()).isEqualTo(endDate);

    checkInstance =
        processInstanceResults.getItems().stream()
            .filter(item -> "demoProcess-2".equals(item.getBpmnProcessId()))
            .findFirst()
            .orElse(null);

    assertThat(checkInstance.getBpmnProcessId()).isEqualTo("demoProcess-2");
    assertThat(checkInstance.getStartDate()).isEqualTo(secondInstanceStartDate);
    assertThat(checkInstance.getEndDate()).isNull();
  }

  @Test
  public void shouldFormatDatesWhenSearchByKey() {
    final ProcessInstance processInstance = dao.byKey(2251799813685251L);

    assertThat(processInstance.getStartDate()).isEqualTo(firstInstanceStartDate);
    assertThat(processInstance.getEndDate()).isEqualTo(endDate);
    assertThat(processInstance.getKey()).isEqualTo(2251799813685251L);
  }
}
