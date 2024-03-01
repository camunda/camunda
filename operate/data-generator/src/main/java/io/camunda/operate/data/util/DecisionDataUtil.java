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
package io.camunda.operate.data.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceInputEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceOutputEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceState;
import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.DecisionStore;
import io.camunda.operate.util.PayloadUtil;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DecisionDataUtil {

  public static final String DECISION_INSTANCE_ID_1_1 = "12121212-1";
  public static final String DECISION_INSTANCE_ID_1_2 = "12121212-2";
  public static final String DECISION_INSTANCE_ID_1_3 = "12121212-3";
  public static final String DECISION_INSTANCE_ID_2_1 = "13131313-1";
  public static final String DECISION_INSTANCE_ID_2_2 = "13131313-2";
  public static final String DECISION_DEFINITION_ID_1 = "decisionDef1";
  public static final String DECISION_DEFINITION_ID_2 = "decisionDef2";
  public static final long PROCESS_INSTANCE_ID = 555555;
  public static final String DECISION_DEFINITION_NAME_1 = "Assign Approver Group";
  public static final String DECISION_ID_1 = "invoice-assign-approver";
  public static final String DECISION_ID_2 = "invoiceClassification";

  public static final String TENANT1 = "tenant1";
  public static final String TENANT2 = "tenant2";
  @Autowired protected DecisionStore decisionStore;
  private Map<Class<? extends OperateEntity>, String> entityToESAliasMap;
  private final Random random = new Random();
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private PayloadUtil payloadUtil;

  public List<OperateEntity> createDecisionDefinitions() {
    final List<OperateEntity> decisionEntities = new ArrayList<>();

    // create DRD version 1
    decisionEntities.add(
        new DecisionRequirementsEntity()
            .setId("1111")
            .setKey(1111L)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setName("Invoice Business Decisions")
            .setVersion(1)
            .setXml(
                payloadUtil.readStringFromClasspath("/usertest/invoiceBusinessDecisions_v_1.dmn"))
            .setResourceName("invoiceBusinessDecisions_v_1.dmn"));
    // create Decisions
    decisionEntities.add(
        new DecisionDefinitionEntity()
            .setId("1222")
            .setKey(1222L)
            .setDecisionId(DECISION_ID_2)
            .setName("Invoice Classification")
            .setVersion(1)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setDecisionRequirementsKey(1111));
    decisionEntities.add(
        new DecisionDefinitionEntity()
            .setId("1333")
            .setKey(1333L)
            .setDecisionId(DECISION_ID_1)
            .setName("Assign Approver Group")
            .setVersion(1)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setDecisionRequirementsKey(1111));

    // create DRD version 2
    decisionEntities.add(
        new DecisionRequirementsEntity()
            .setId("2222")
            .setKey(2222L)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setName("Invoice Business Decisions")
            .setVersion(2)
            .setXml(
                payloadUtil.readStringFromClasspath("/usertest/invoiceBusinessDecisions_v_2.dmn"))
            .setResourceName("invoiceBusinessDecisions_v_2.dmn"));
    // create Decisions
    decisionEntities.add(
        new DecisionDefinitionEntity()
            .setId("2222")
            .setKey(2222L)
            .setDecisionId(DECISION_ID_2)
            .setName("Invoice Classification")
            .setVersion(2)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setDecisionRequirementsKey(2222));
    decisionEntities.add(
        new DecisionDefinitionEntity()
            .setId("2333")
            .setKey(2333L)
            .setDecisionId(DECISION_ID_1)
            .setName("Assign Approver Group")
            .setVersion(2)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setDecisionRequirementsKey(2222));

    return decisionEntities;
  }

  public List<DecisionInstanceEntity> createDecisionInstances() {
    final List<DecisionInstanceEntity> result = new ArrayList<>();

    // 3 EVALUATED, 1 decision1 + 2 decision2, 2 version1 + 1 version2
    result.add(
        createDecisionInstance(
            DECISION_INSTANCE_ID_1_1,
            DecisionInstanceState.EVALUATED,
            DECISION_DEFINITION_NAME_1,
            OffsetDateTime.now(),
            DECISION_DEFINITION_ID_1,
            1,
            DECISION_ID_1,
            35467,
            PROCESS_INSTANCE_ID,
            TENANT1));
    result.add(
        createDecisionInstance(
            DECISION_INSTANCE_ID_1_2,
            DecisionInstanceState.EVALUATED,
            "Invoice Classification",
            OffsetDateTime.now(),
            DECISION_DEFINITION_ID_2,
            1,
            DECISION_ID_2,
            35467,
            random.nextInt(1000),
            TENANT2));
    result.add(
        createDecisionInstance(
            DECISION_INSTANCE_ID_1_3,
            DecisionInstanceState.EVALUATED,
            "Invoice Classification",
            OffsetDateTime.now(),
            DECISION_DEFINITION_ID_2,
            2,
            DECISION_ID_2,
            35467,
            random.nextInt(1000),
            TENANT1));
    // 2 FAILED
    result.add(
        createDecisionInstance(
            DECISION_INSTANCE_ID_2_1,
            DecisionInstanceState.FAILED,
            DECISION_DEFINITION_NAME_1,
            OffsetDateTime.now(),
            DECISION_DEFINITION_ID_1,
            1,
            DECISION_ID_1,
            35467,
            PROCESS_INSTANCE_ID,
            TENANT2));
    result.add(
        createDecisionInstance(
            DECISION_INSTANCE_ID_2_2,
            DecisionInstanceState.FAILED,
            "Invoice Classification",
            OffsetDateTime.now(),
            DECISION_DEFINITION_ID_2,
            2,
            DECISION_ID_2,
            35467,
            random.nextInt(1000),
            TENANT1));

    return result;
  }

  public DecisionInstanceEntity createDecisionInstance(final OffsetDateTime evaluationDate) {
    return createDecisionInstance(
        random.nextInt(1) == 0 ? DecisionInstanceState.EVALUATED : DecisionInstanceState.FAILED,
        UUID.randomUUID().toString(),
        evaluationDate,
        random.nextInt(1) == 0 ? DECISION_DEFINITION_ID_1 : DECISION_DEFINITION_ID_2,
        1,
        UUID.randomUUID().toString(),
        random.nextInt(1000),
        random.nextInt(1000));
  }

  public DecisionInstanceEntity createDecisionInstance(
      final DecisionInstanceState state,
      final String decisionName,
      final OffsetDateTime evaluationDate,
      final String decisionDefinitionId,
      final int decisionVersion,
      final String decisionId,
      final long processDefinitionKey,
      final long processInstanceKey) {
    return createDecisionInstance(
        String.valueOf(random.nextInt(1000)) + "-1",
        state,
        decisionName,
        evaluationDate,
        decisionDefinitionId,
        decisionVersion,
        decisionId,
        processDefinitionKey,
        processInstanceKey,
        null);
  }

  private DecisionInstanceEntity createDecisionInstance(
      final String decisionInstanceId,
      final DecisionInstanceState state,
      final String decisionName,
      final OffsetDateTime evaluationDate,
      final String decisionDefinitionId,
      final int decisionVersion,
      final String decisionId,
      final long processDefinitionKey,
      final long processInstanceKey,
      final String tenantId) {

    final List<DecisionInstanceInputEntity> inputs = new ArrayList<>();
    inputs.add(
        new DecisionInstanceInputEntity()
            .setId("InputClause_0og2hn3")
            .setName("Invoice Classification")
            .setValue("day-to-day expense"));
    inputs.add(
        new DecisionInstanceInputEntity()
            .setId("InputClause_0og2hn3")
            .setName("Invoice Classification")
            .setValue("budget"));
    final List<DecisionInstanceOutputEntity> outputs = new ArrayList<>();
    outputs.add(
        new DecisionInstanceOutputEntity()
            .setId("OutputClause_1cthd0w")
            .setName("Approver Group")
            .setValue("budget")
            .setRuleIndex(2)
            .setRuleId("row-49839158-5"));
    outputs.add(
        new DecisionInstanceOutputEntity()
            .setId("OutputClause_1cthd0w")
            .setName("Approver Group")
            .setValue("sales")
            .setRuleIndex(1)
            .setRuleId("row-49839158-6"));
    outputs.add(
        new DecisionInstanceOutputEntity()
            .setId("OutputClause_1cthd0w")
            .setName("Approver Group")
            .setValue("accounting")
            .setRuleIndex(1)
            .setRuleId("row-49839158-1"));

    String evaluationFailure = null;
    if (state == DecisionInstanceState.FAILED) {
      evaluationFailure = "Variable not found: invoiceClassification";
    }

    return new DecisionInstanceEntity()
        .setId(decisionInstanceId)
        .setKey(Long.valueOf(decisionInstanceId.split("-")[0]))
        .setExecutionIndex(Integer.valueOf(decisionInstanceId.split("-")[1]))
        .setState(state)
        .setEvaluationFailure(evaluationFailure)
        .setDecisionName(decisionName)
        .setDecisionVersion(decisionVersion)
        .setDecisionType(DecisionType.DECISION_TABLE)
        .setEvaluationDate(evaluationDate)
        .setDecisionDefinitionId(decisionDefinitionId)
        .setDecisionId(decisionId)
        .setDecisionRequirementsId("1111")
        .setDecisionRequirementsKey(1111)
        .setElementId("taskA")
        .setElementInstanceKey(76543)
        .setEvaluatedInputs(inputs)
        .setEvaluatedOutputs(outputs)
        .setPosition(1000L)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setResult("{\"total\": 100.0}")
        .setTenantId(tenantId);
  }

  public void persistOperateEntities(final List<? extends OperateEntity> operateEntities)
      throws PersistenceException {
    try {
      final BatchRequest batchRequest = decisionStore.newBatchRequest();
      for (final OperateEntity<?> entity : operateEntities) {
        final String alias = getEntityToESAliasMap().get(entity.getClass());
        if (alias == null) {
          throw new RuntimeException("Index not configured for " + entity.getClass().getName());
        }
        batchRequest.add(alias, entity);
      }
      batchRequest.execute();
    } catch (final Exception ex) {
      throw new PersistenceException(ex);
    }
  }

  public Map<Class<? extends OperateEntity>, String> getEntityToESAliasMap() {
    if (entityToESAliasMap == null) {
      entityToESAliasMap = new HashMap<>();
      entityToESAliasMap.put(
          DecisionInstanceEntity.class, decisionInstanceTemplate.getFullQualifiedName());
      entityToESAliasMap.put(
          DecisionRequirementsEntity.class, decisionRequirementsIndex.getFullQualifiedName());
      entityToESAliasMap.put(DecisionDefinitionEntity.class, decisionIndex.getFullQualifiedName());
    }
    return entityToESAliasMap;
  }
}
