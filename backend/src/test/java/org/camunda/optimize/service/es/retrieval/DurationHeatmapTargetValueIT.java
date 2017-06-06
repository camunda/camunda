package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.optimize.query.DurationHeatmapTargetValueDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.elasticsearch.action.get.GetResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class DurationHeatmapTargetValueIT {

  private final String ACTIVITY_ID_1 = "act1";
  private final String ACTIVITY_ID_2 = "act2";
  private final String TARGET_VALUE_1 = "50";
  private final String TARGET_VALUE_2 = "100";
  private final String TARGET_VALUE_3 = "200";
  private final String PROCESS_DEFINITION_ID = "procDef";

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void writerIsAddingDoc() {
    // given
    setupTargetValueDto();

    // then
    GetResponse response = elasticSearchRule.getClient()
      .prepareGet(elasticSearchRule.getOptimizeIndex(),
          elasticSearchRule.getDurationHeatmapTargetValueType(),
        PROCESS_DEFINITION_ID)
      .get();

    assertThat(response.isExists(), is(true));
  }

  @Test
  public void writeAndThenReadGivesTheSameResults() {
    // given
    setupTargetValueDto();

    // when
    DurationHeatmapTargetValueDto dto = getDurationHeatmapTargetValueDto(PROCESS_DEFINITION_ID);

    // then
    assertThat(dto.getProcessDefinitionId(), is(PROCESS_DEFINITION_ID));
    assertThat(dto.getTargetValues().get(ACTIVITY_ID_1), is(TARGET_VALUE_1));
    assertThat(dto.getTargetValues().get(ACTIVITY_ID_2), is(TARGET_VALUE_2));
  }

  private DurationHeatmapTargetValueDto getDurationHeatmapTargetValueDto(String processDefinitionId) {
    String token = embeddedOptimizeRule.authenticateAdmin();
    Response response = embeddedOptimizeRule.target("process-definition/" + processDefinitionId + "/heatmap/duration/target-value-comparison")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get();
    return response.readEntity(DurationHeatmapTargetValueDto.class);
  }

  private void persistValue(DurationHeatmapTargetValueDto targetValueDto) {
    String token = embeddedOptimizeRule.authenticateAdmin();
    Entity<DurationHeatmapTargetValueDto> entity = Entity.entity(targetValueDto, MediaType.APPLICATION_JSON);
    Response response = embeddedOptimizeRule.target("process-definition/heatmap/duration/target-value")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .put(entity);
    assertThat(response.getStatus(),is(200));
  }

  @Test
  public void addingToSameProcDefTwiceIsOverwriting() {
    // given
    DurationHeatmapTargetValueDto targetValueDto = setupTargetValueDto();
    targetValueDto.getTargetValues().replace(ACTIVITY_ID_1, TARGET_VALUE_3);
    persistValue(targetValueDto);

    // when
    DurationHeatmapTargetValueDto dto = getDurationHeatmapTargetValueDto(PROCESS_DEFINITION_ID);

    // then
    assertThat(dto.getProcessDefinitionId(), is(PROCESS_DEFINITION_ID));
    assertThat(dto.getTargetValues().get(ACTIVITY_ID_1), is(TARGET_VALUE_3));
  }

  @Test
  public void ifNoTargetValuesAreAvailableAnEmptyMapIsReturned() {
    // given no target values

    // when
    DurationHeatmapTargetValueDto dto = getDurationHeatmapTargetValueDto(PROCESS_DEFINITION_ID);

    // then
    assertThat(dto.getProcessDefinitionId(), is(PROCESS_DEFINITION_ID));
    assertThat(dto.getTargetValues().size(), is(0));
  }

  private DurationHeatmapTargetValueDto setupTargetValueDto() {
    DurationHeatmapTargetValueDto durationHeatmapTargetValueDto = new DurationHeatmapTargetValueDto();
    durationHeatmapTargetValueDto.setProcessDefinitionId(PROCESS_DEFINITION_ID);

    Map<String, String> targetValue = new HashMap<>();
    targetValue.put(ACTIVITY_ID_1, TARGET_VALUE_1);
    targetValue.put(ACTIVITY_ID_2, TARGET_VALUE_2);
    durationHeatmapTargetValueDto.setTargetValues(targetValue);

    persistValue(durationHeatmapTargetValueDto);

    return durationHeatmapTargetValueDto;
  }

}
