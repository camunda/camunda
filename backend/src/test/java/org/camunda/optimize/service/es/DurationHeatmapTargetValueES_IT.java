package org.camunda.optimize.service.es;

import org.camunda.optimize.dto.optimize.DurationHeatmapTargetValueDto;
import org.camunda.optimize.service.es.reader.DurationHeatmapTargetValueReader;
import org.camunda.optimize.service.es.writer.DurationHeatmapTargetValueWriter;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.elasticsearch.action.get.GetResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/es/it/es-it-applicationContext.xml"})
public class DurationHeatmapTargetValueES_IT {

  private final String ACTIVITY_ID_1 = "act1";
  private final String ACTIVITY_ID_2 = "act2";
  private final String TARGET_VALUE_1 = "50";
  private final String TARGET_VALUE_2 = "100";
  private final String TARGET_VALUE_3 = "200";
  private final String PROCESS_DEFINITION_ID = "procDef";
  @Rule
  public ElasticSearchIntegrationTestRule rule = new ElasticSearchIntegrationTestRule();
  @Autowired
  private DurationHeatmapTargetValueReader targetValueReader;
  @Autowired
  private DurationHeatmapTargetValueWriter targetValueWriter;
  @Autowired
  private ConfigurationService configurationService;

  @Test
  public void writerIsAddingDoc() {
    // given
    setupTargetValueDto();

    // then
    GetResponse response = rule.getClient()
      .prepareGet(configurationService.getOptimizeIndex(),
        configurationService.getDurationHeatmapTargetValueType(),
        PROCESS_DEFINITION_ID)
      .get();

    assertThat(response.isExists(), is(true));
  }

  @Test
  public void writeAndThenReadGivesTheSameResults() {
    // given
    setupTargetValueDto();

    // when
    DurationHeatmapTargetValueDto dto = targetValueReader.getTargetValues(PROCESS_DEFINITION_ID);

    // then
    assertThat(dto.getProcessDefinitionId(), is(PROCESS_DEFINITION_ID));
    assertThat(dto.getTargetValues().get(ACTIVITY_ID_1), is(TARGET_VALUE_1));
    assertThat(dto.getTargetValues().get(ACTIVITY_ID_2), is(TARGET_VALUE_2));
  }

  @Test
  public void addingToSameProcDefTwiceIsOverwriting() {
    // given
    DurationHeatmapTargetValueDto targetValueDto = setupTargetValueDto();
    targetValueDto.getTargetValues().replace(ACTIVITY_ID_1, TARGET_VALUE_3);
    targetValueWriter.persistTargetValue(targetValueDto);

    // when
    DurationHeatmapTargetValueDto dto = targetValueReader.getTargetValues(PROCESS_DEFINITION_ID);

    // then
    assertThat(dto.getProcessDefinitionId(), is(PROCESS_DEFINITION_ID));
    assertThat(dto.getTargetValues().get(ACTIVITY_ID_1), is(TARGET_VALUE_3));
  }

  @Test
  public void ifNoTargetValuesAreAvailableAnEmptyMapIsReturned() {
    // given no target values

    // when
    DurationHeatmapTargetValueDto dto = targetValueReader.getTargetValues(PROCESS_DEFINITION_ID);

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

    targetValueWriter.persistTargetValue(durationHeatmapTargetValueDto);

    return durationHeatmapTargetValueDto;
  }

}
