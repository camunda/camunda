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
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IncidentDaoIT extends OperateSearchAbstractIT {
  @Autowired private IncidentDao dao;

  @Autowired private IncidentTemplate incidentIndex;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentIndex.getFullQualifiedName(),
        new IncidentEntity()
            .setKey(7147483647L)
            .setProcessDefinitionKey(5147483647L)
            .setProcessInstanceKey(6147483647L)
            .setErrorType(ErrorType.JOB_NO_RETRIES)
            .setState(IncidentState.ACTIVE)
            .setErrorMessage("Some error")
            .setTenantId(DEFAULT_TENANT_ID)
            .setCreationTime(OffsetDateTime.now())
            .setJobKey(2251799813685260L));

    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentIndex.getFullQualifiedName(),
        new IncidentEntity()
            .setKey(7147483648L)
            .setProcessDefinitionKey(5147483648L)
            .setProcessInstanceKey(6147483648L)
            .setErrorType(ErrorType.JOB_NO_RETRIES)
            .setState(IncidentState.ACTIVE)
            .setErrorMessage("Another error")
            .setTenantId(DEFAULT_TENANT_ID)
            .setCreationTime(OffsetDateTime.now())
            .setJobKey(3251799813685260L));

    searchContainerManager.refreshIndices("*operate-incident*");
  }

  @Test
  public void shouldReturnIncidents() {
    Results<Incident> incidentResults = dao.search(new Query<>());

    assertThat(incidentResults.getItems()).hasSize(2);

    Incident checkIncident =
        incidentResults.getItems().stream()
            .filter(item -> 5147483647L == item.getProcessDefinitionKey())
            .findFirst()
            .orElse(null);
    assertThat(checkIncident)
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483647L, 6147483647L, "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);

    checkIncident =
        incidentResults.getItems().stream()
            .filter(item -> 5147483648L == item.getProcessDefinitionKey())
            .findFirst()
            .orElse(null);

    assertThat(checkIncident)
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldSortIncidentsDesc() {
    Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>()
                .setSort(
                    Query.Sort.listOf(Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.DESC)));

    assertThat(incidentResults.getItems()).hasSize(2);

    assertThat(incidentResults.getItems().get(0))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);

    assertThat(incidentResults.getItems().get(1))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483647L, 6147483647L, "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldSortIncidentsAsc() {
    Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>()
                .setSort(Query.Sort.listOf(Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.ASC)));

    assertThat(incidentResults.getItems()).hasSize(2);

    assertThat(incidentResults.getItems().get(0))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483647L, 6147483647L, "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);

    assertThat(incidentResults.getItems().get(1))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldPageIncidents() {
    Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>()
                .setSort(Query.Sort.listOf(Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.DESC))
                .setSize(1));

    assertThat(incidentResults.getItems()).hasSize(1);
    assertThat(incidentResults.getTotal()).isEqualTo(2);

    Object[] searchAfter = incidentResults.getSortValues();
    assertThat(incidentResults.getItems().get(0).getProcessDefinitionKey().toString())
        .isEqualTo(searchAfter[0].toString());

    assertThat(incidentResults.getItems().get(0))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);

    incidentResults =
        dao.search(
            new Query<Incident>()
                .setSort(Query.Sort.listOf(Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.DESC))
                .setSize(1)
                .setSearchAfter(searchAfter));

    assertThat(incidentResults.getItems()).hasSize(1);
    assertThat(incidentResults.getTotal()).isEqualTo(2);

    assertThat(incidentResults.getItems().get(0))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483647L, 6147483647L, "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldFilterIncidents() {
    Results<Incident> incidentResults =
        dao.search(
            new Query<Incident>().setFilter(new Incident().setProcessInstanceKey(6147483648L)));

    assertThat(incidentResults.getTotal()).isEqualTo(1);

    assertThat(incidentResults.getItems().get(0))
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldReturnByKey() {
    Incident checkIncident = dao.byKey(7147483648L);

    assertThat(checkIncident).isNotNull();
    assertThat(checkIncident)
        .extracting(
            "processDefinitionKey", "processInstanceKey", "type", "state", "message", "tenantId")
        .containsExactly(
            5147483648L,
            6147483648L,
            "JOB_NO_RETRIES",
            "ACTIVE",
            "Another error",
            DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }
}
