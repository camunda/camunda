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
package io.camunda.operate.it;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.writer.DecisionWriter;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DecisionWriterIT extends OperateSearchAbstractIT {

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired private DecisionWriter decisionWriter;

  @Test
  public void shouldDeleteDecisionRequirements() throws IOException {
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionRequirementsIndex.getFullQualifiedName(),
        new DecisionRequirementsEntity()
            .setId("2251799813685249")
            .setKey(2251799813685249L)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setName("Invoice Business Decisions")
            .setVersion(1)
            .setResourceName("invoiceBusinessDecisions_v_1.dmn")
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-decision*");

    long deleted = decisionWriter.deleteDecisionRequirements(2251799813685249L);

    assertThat(deleted).isEqualTo(1);
  }

  @Test
  public void shouldDeleteDecisionDefinitions() throws IOException {
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionIndex.getFullQualifiedName(),
        new DecisionDefinitionEntity()
            .setId("2251799813685250")
            .setKey(2251799813685250L)
            .setDecisionId("invoiceAssignApprover")
            .setName("Assign Approver Group")
            .setVersion(1)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setDecisionRequirementsKey(2251799813685249L)
            .setTenantId(DEFAULT_TENANT_ID));
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionIndex.getFullQualifiedName(),
        new DecisionDefinitionEntity()
            .setId("2251799813685251")
            .setKey(2251799813685251L)
            .setDecisionId("invoiceClassification")
            .setName("Invoice Classification")
            .setVersion(1)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setDecisionRequirementsKey(2251799813685249L)
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-decision*");

    long deleted = decisionWriter.deleteDecisionDefinitionsFor(2251799813685249L);
    // then
    assertThat(deleted).isEqualTo(2);
  }

  @Test
  public void shouldDeleteDecisionInstances() throws IOException {
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionInstanceTemplate.getFullQualifiedName(),
        new DecisionInstanceEntity()
            .setId("2251799813685262-1")
            .setKey(2251799813685262L)
            .setState(io.camunda.operate.entities.dmn.DecisionInstanceState.EVALUATED)
            .setEvaluationDate(OffsetDateTime.now())
            .setProcessDefinitionKey(2251799813685253L)
            .setDecisionRequirementsKey(2251799813685249L)
            .setProcessInstanceKey(2251799813685255L));
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionInstanceTemplate.getFullQualifiedName(),
        new DecisionInstanceEntity()
            .setId("2251799813685262-2")
            .setKey(2251799813685262L)
            .setState(io.camunda.operate.entities.dmn.DecisionInstanceState.EVALUATED)
            .setEvaluationDate(OffsetDateTime.now())
            .setDecisionRequirementsKey(2251799813685249L)
            .setProcessDefinitionKey(2251799813685253L)
            .setProcessInstanceKey(2251799813685255L));

    searchContainerManager.refreshIndices("*operate-decision*");

    long deleted = decisionWriter.deleteDecisionInstancesFor(2251799813685249L);
    // then
    assertThat(deleted).isEqualTo(2);
  }

  @Test
  public void shouldNotDeleteWhenNothingFound() throws IOException {
    long decisionRequirementsKey = 123L;
    // when
    long deleted1 = decisionWriter.deleteDecisionRequirements(decisionRequirementsKey);
    long deleted2 = decisionWriter.deleteDecisionDefinitionsFor(decisionRequirementsKey);
    long deleted3 = decisionWriter.deleteDecisionInstancesFor(decisionRequirementsKey);
    // then
    assertThat(deleted1).isZero();
    assertThat(deleted2).isZero();
    assertThat(deleted3).isZero();
  }
}
