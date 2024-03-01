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
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class VariableDaoIT extends OperateSearchAbstractIT {
  @Autowired private VariableDao dao;

  @Autowired private VariableTemplate variableIndex;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    String indexName = variableIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new VariableEntity()
            .setKey(5147483647L)
            .setProcessInstanceKey(4147483647L)
            .setScopeKey(4147483647L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("customerId")
            .setValue("\"23\""));
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new VariableEntity()
            .setKey(5147483648L)
            .setProcessInstanceKey(4147483647L)
            .setScopeKey(4147483647L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("orderId")
            .setValue("\"5\""));
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new VariableEntity()
            .setKey(5147483649L)
            .setProcessInstanceKey(4147483649L)
            .setScopeKey(4147483649L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("k1")
            .setValue("\"v1\""));

    searchContainerManager.refreshIndices("*operate-variable*");
  }

  @Test
  public void shouldReturnVariables() {
    Results<Variable> variableResults = dao.search(new Query<>());
    assertThat(variableResults.getItems()).hasSize(3);

    assertThat(variableResults.getItems())
        .extracting(Variable.NAME)
        .containsExactlyInAnyOrder("customerId", "k1", "orderId");
  }

  @Test
  public void shouldSortVariablesDesc() {
    Results<Variable> variableResults =
        dao.search(new Query<Variable>().setSort(Query.Sort.listOf("name", Query.Sort.Order.DESC)));

    assertThat(variableResults.getItems()).hasSize(3);
    assertThat(variableResults.getItems())
        .extracting(Variable.NAME)
        .containsExactly("orderId", "k1", "customerId");
  }

  @Test
  public void shouldSortVariablesAsc() {
    Results<Variable> variableResults =
        dao.search(new Query<Variable>().setSort(Query.Sort.listOf("name", Query.Sort.Order.ASC)));

    assertThat(variableResults.getItems()).hasSize(3);
    assertThat(variableResults.getItems())
        .extracting(Variable.NAME)
        .containsExactly("customerId", "k1", "orderId");
  }

  @Test
  public void shouldReturnVariableByKey() {
    Long key = 5147483648L;
    Variable variable = dao.byKey(key);

    assertThat(variable.getValue()).isEqualTo("\"5\"");
    assertThat(variable.getKey()).isEqualTo(key);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }

  @Test
  public void shouldFilterVariables() {
    Results<Variable> variableResults =
        dao.search(new Query<Variable>().setFilter(new Variable().setName("orderId")));

    assertThat(variableResults.getItems()).hasSize(1);
    assertThat(variableResults.getItems().get(0).getName()).isEqualTo("orderId");
  }

  @Test
  public void shouldPageVariables() {
    Results<Variable> variableResults =
        dao.search(
            new Query<Variable>()
                .setSize(2)
                .setSort(Query.Sort.listOf("name", Query.Sort.Order.DESC)));

    assertThat(variableResults.getTotal()).isEqualTo(3);
    assertThat(variableResults.getItems()).hasSize(2);
    assertThat(variableResults.getItems())
        .extracting(Variable.NAME)
        .containsExactly("orderId", "k1");

    Object[] searchAfter = variableResults.getSortValues();
    assertThat(String.valueOf(variableResults.getItems().get(1).getName()))
        .isEqualTo(String.valueOf(searchAfter[0]));

    Results<Variable> nextResults =
        dao.search(
            new Query<Variable>()
                .setSearchAfter(searchAfter)
                .setSize(2)
                .setSort(Query.Sort.listOf("name", Query.Sort.Order.DESC)));

    assertThat(nextResults.getTotal()).isEqualTo(3);
    assertThat(nextResults.getItems()).hasSize(1);
    assertThat(nextResults.getItems().get(0).getName()).isEqualTo("customerId");
  }
}
