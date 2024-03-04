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
package io.camunda.operate.it;

import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_ID_1;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_ID_2;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_1_1;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_1_2;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_1_3;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_2_1;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_2_2;
import static io.camunda.operate.webapp.rest.DecisionInstanceRestService.DECISION_INSTANCE_URL;
import static io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto.DECISION_INSTANCE_INPUT_DTO_COMPARATOR;
import static io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto.DECISION_INSTANCE_OUTPUT_DTO_COMPARATOR;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.data.util.DecisionDataUtil;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.webapp.rest.dto.dmn.DRDDataEntryDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class DecisionInstanceReaderIT extends OperateAbstractIT {

  private static final String QUERY_DECISION_INSTANCES_URL = DECISION_INSTANCE_URL + "/%s";

  @Rule public SearchTestRule searchTestRule = new SearchTestRule();

  @Autowired private DecisionDataUtil testDataUtil;

  @Test
  public void testGetDecisionInstanceByCorrectId() throws Exception {
    final DecisionInstanceEntity entity = createData();

    final MvcResult mvcResult = getRequest(getQuery(DECISION_INSTANCE_ID_1_1));
    DecisionInstanceDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getDecisionDefinitionId()).isEqualTo(entity.getDecisionDefinitionId());
    assertThat(response.getDecisionId()).isEqualTo(entity.getDecisionId());
    assertThat(response.getDecisionName()).isEqualTo(entity.getDecisionName());
    assertThat(response.getDecisionVersion()).isEqualTo(entity.getDecisionVersion());
    assertThat(response.getDecisionType()).isEqualTo(entity.getDecisionType());
    assertThat(response.getErrorMessage()).isEqualTo(entity.getEvaluationFailure());
    assertThat(response.getEvaluationDate())
        .isEqualTo(entity.getEvaluationDate().truncatedTo(ChronoUnit.MILLIS));
    assertThat(response.getId()).isEqualTo(entity.getId());
    assertThat(response.getProcessInstanceId())
        .isEqualTo(String.valueOf(entity.getProcessInstanceKey()));
    assertThat(response.getResult()).isEqualTo(entity.getResult());
    assertThat(response.getState().toString()).isEqualTo(entity.getState().toString());

    assertThat(response.getEvaluatedInputs()).hasSize(2);
    assertThat(response.getEvaluatedInputs())
        .isSortedAccordingTo(DECISION_INSTANCE_INPUT_DTO_COMPARATOR);
    assertThat(response.getEvaluatedOutputs()).hasSize(3);
    assertThat(response.getEvaluatedOutputs())
        .isSortedAccordingTo(DECISION_INSTANCE_OUTPUT_DTO_COMPARATOR);
  }

  @Test
  public void testGetDecisionInstanceDrdData() throws Exception {
    createData();

    // query completed instances
    MvcResult mvcResult = getRequest(getDrdDataQuery(DECISION_INSTANCE_ID_1_1));
    Map<String, List<DRDDataEntryDto>> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(2);
    assertThat(response.get(DECISION_ID_1)).isNotNull();
    assertThat(response.get(DECISION_ID_1)).hasSize(1);
    assertThat(response.get(DECISION_ID_1).get(0).getDecisionInstanceId())
        .isEqualTo(DECISION_INSTANCE_ID_1_1);
    assertThat(response.get(DECISION_ID_1).get(0).getState())
        .isEqualTo(DecisionInstanceState.EVALUATED);

    assertThat(response.get(DECISION_ID_2)).isNotNull();
    assertThat(response.get(DECISION_ID_2)).hasSize(2);
    assertThat(response.get(DECISION_ID_2).get(0).getDecisionInstanceId())
        .isEqualTo(DECISION_INSTANCE_ID_1_2);
    assertThat(response.get(DECISION_ID_2).get(0).getState())
        .isEqualTo(DecisionInstanceState.EVALUATED);
    assertThat(response.get(DECISION_ID_2).get(1).getDecisionInstanceId())
        .isEqualTo(DECISION_INSTANCE_ID_1_3);
    assertThat(response.get(DECISION_ID_2).get(1).getState())
        .isEqualTo(DecisionInstanceState.EVALUATED);

    // query completed and failed
    mvcResult = getRequest(getDrdDataQuery(DECISION_INSTANCE_ID_2_1));
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(2);
    assertThat(response.get(DECISION_ID_1)).isNotNull();
    assertThat(response.get(DECISION_ID_1)).hasSize(1);
    assertThat(response.get(DECISION_ID_1).get(0).getDecisionInstanceId())
        .isEqualTo(DECISION_INSTANCE_ID_2_1);
    assertThat(response.get(DECISION_ID_1).get(0).getState())
        .isEqualTo(DecisionInstanceState.FAILED);

    assertThat(response.get(DECISION_ID_2)).isNotNull();
    assertThat(response.get(DECISION_ID_2).get(0).getDecisionInstanceId())
        .isEqualTo(DECISION_INSTANCE_ID_2_2);
    assertThat(response.get(DECISION_ID_2).get(0).getState())
        .isEqualTo(DecisionInstanceState.FAILED);
  }

  @Test
  public void testGetDecisionInstanceDrdDataByWrongId() throws Exception {
    createData();
    MvcResult mvcResult =
        getRequestShouldFailWithException(getDrdDataQuery("55555-1"), NotFoundException.class);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("Decision instance nor found: 55555-1");
  }

  @Test
  public void testGetDecisionInstanceByWrongId() throws Exception {
    createData();

    getRequestShouldFailWithException(getQuery("867293586"), NotFoundException.class);
  }

  private DecisionInstanceEntity createData() throws PersistenceException {
    final List<DecisionInstanceEntity> decisionInstances = testDataUtil.createDecisionInstances();
    searchTestRule.persistOperateEntitiesNew(decisionInstances);
    return decisionInstances.get(0);
  }

  private String getQuery(String decisionInstanceId) {
    return String.format(QUERY_DECISION_INSTANCES_URL, decisionInstanceId);
  }

  private String getDrdDataQuery(String decisionInstanceId) {
    return String.format(QUERY_DECISION_INSTANCES_URL + "/drd-data", decisionInstanceId);
  }
}
