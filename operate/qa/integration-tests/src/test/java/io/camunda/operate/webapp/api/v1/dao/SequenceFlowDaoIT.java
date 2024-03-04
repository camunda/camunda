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

import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SequenceFlowDaoIT extends OperateSearchAbstractIT {

  @Autowired private SequenceFlowDao dao;

  @Autowired private SequenceFlowTemplate sequenceFlowIndex;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    String indexName = sequenceFlowIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new SequenceFlowEntity()
            .setId("2251799813685253_sequenceFlow_01")
            .setActivityId("sequenceFlow_01")
            .setProcessInstanceKey(2251799813685253L)
            .setTenantId(DEFAULT_TENANT_ID));
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new SequenceFlowEntity()
            .setId("2251799813685253_sequenceFlow_02")
            .setActivityId("sequenceFlow_02")
            .setProcessInstanceKey(2251799813685253L)
            .setTenantId(DEFAULT_TENANT_ID));
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new SequenceFlowEntity()
            .setId("2251799813685253_sequenceFlow_03")
            .setActivityId("sequenceFlow_03")
            .setProcessInstanceKey(2251799813685253L)
            .setTenantId(DEFAULT_TENANT_ID));
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new SequenceFlowEntity()
            .setId("2251799813685253_sequenceFlow_04")
            .setActivityId("sequenceFlow_04")
            .setProcessInstanceKey(2251799813685253L)
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-sequence*");
  }

  @Test
  public void shouldReturnSequenceFlows() {
    Results<SequenceFlow> sequenceFlowResults = dao.search(new Query<>());

    assertThat(sequenceFlowResults.getItems()).hasSize(4);
    assertThat(sequenceFlowResults.getItems())
        .extracting(SequenceFlow.ACTIVITY_ID)
        .containsExactlyInAnyOrder(
            "sequenceFlow_01", "sequenceFlow_02", "sequenceFlow_03", "sequenceFlow_04");
  }

  @Test
  public void shouldFilterSequenceFlows() {
    Results<SequenceFlow> sequenceFlowResults =
        dao.search(
            new Query<SequenceFlow>()
                .setFilter(new SequenceFlow().setActivityId("sequenceFlow_01")));

    assertThat(sequenceFlowResults.getItems()).hasSize(1);

    assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_01");
  }

  @Test
  public void shouldSortSequenceFlowsDesc() {
    Results<SequenceFlow> sequenceFlowResults =
        dao.search(
            new Query<SequenceFlow>()
                .setSort(Query.Sort.listOf(SequenceFlow.ACTIVITY_ID, Query.Sort.Order.DESC)));

    assertThat(sequenceFlowResults.getItems()).hasSize(4);
    assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_04");
    assertThat(sequenceFlowResults.getItems().get(1).getActivityId()).isEqualTo("sequenceFlow_03");
    assertThat(sequenceFlowResults.getItems().get(2).getActivityId()).isEqualTo("sequenceFlow_02");
    assertThat(sequenceFlowResults.getItems().get(3).getActivityId()).isEqualTo("sequenceFlow_01");
  }

  @Test
  public void shouldSortSequenceFlowsAsc() {
    Results<SequenceFlow> sequenceFlowResults =
        dao.search(
            new Query<SequenceFlow>()
                .setSort(Query.Sort.listOf(SequenceFlow.ACTIVITY_ID, Query.Sort.Order.ASC)));

    assertThat(sequenceFlowResults.getItems()).hasSize(4);
    assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_01");
    assertThat(sequenceFlowResults.getItems().get(1).getActivityId()).isEqualTo("sequenceFlow_02");
    assertThat(sequenceFlowResults.getItems().get(2).getActivityId()).isEqualTo("sequenceFlow_03");
    assertThat(sequenceFlowResults.getItems().get(3).getActivityId()).isEqualTo("sequenceFlow_04");
  }

  @Test
  public void shouldPageSequenceFlows() {
    Results<SequenceFlow> sequenceFlowResults =
        dao.search(
            new Query<SequenceFlow>()
                .setSort(Query.Sort.listOf(SequenceFlow.ACTIVITY_ID, Query.Sort.Order.DESC))
                .setSize(3));

    assertThat(sequenceFlowResults.getItems()).hasSize(3);
    assertThat(sequenceFlowResults.getTotal()).isEqualTo(4);

    assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_04");
    assertThat(sequenceFlowResults.getItems().get(1).getActivityId()).isEqualTo("sequenceFlow_03");
    assertThat(sequenceFlowResults.getItems().get(2).getActivityId()).isEqualTo("sequenceFlow_02");

    Object[] searchAfter = sequenceFlowResults.getSortValues();
    assertThat(String.valueOf(sequenceFlowResults.getItems().get(2).getActivityId()))
        .isEqualTo(String.valueOf(searchAfter[0]));

    sequenceFlowResults =
        dao.search(
            new Query<SequenceFlow>()
                .setSort(Query.Sort.listOf(SequenceFlow.ACTIVITY_ID, Query.Sort.Order.DESC))
                .setSize(3)
                .setSearchAfter(searchAfter));

    assertThat(sequenceFlowResults.getItems()).hasSize(1);
    assertThat(sequenceFlowResults.getTotal()).isEqualTo(4);

    assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_01");
  }
}
