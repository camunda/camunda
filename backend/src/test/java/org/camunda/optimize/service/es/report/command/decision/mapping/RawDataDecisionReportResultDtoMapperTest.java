package org.camunda.optimize.service.es.report.command.decision.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RawDataDecisionReportResultDtoMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testMapFromSearchResponse_hitCountNotEqualTotalCount() {
    // given
    final Long rawDataLimit = 2L;
    final Long actualInstanceCount = 3L;
    final RawDecisionDataResultDtoMapper mapper = new RawDecisionDataResultDtoMapper(rawDataLimit);

    final SearchResponse searchResponse = createSearchResponseMock(rawDataLimit.intValue(), actualInstanceCount);

    // when
    final RawDataDecisionReportResultDto result = mapper.mapFrom(searchResponse, objectMapper);

    // then
    assertThat(result.getData().size(), is(rawDataLimit.intValue()));
    assertThat(result.getDecisionInstanceCount(), is(actualInstanceCount));
  }

  @Test
  public void testAllInputAndOutVariablesAreAvailableAtEachInstance() {
    // given
    final Long rawDataLimit = 2L;
    final Long actualInstanceCount = 3L;
    final RawDecisionDataResultDtoMapper mapper = new RawDecisionDataResultDtoMapper(rawDataLimit);

    final List<DecisionInstanceDto> decisionInstances = IntStream.rangeClosed(1, rawDataLimit.intValue())
      .mapToObj(i -> {
        DecisionInstanceDto instanceDto = new DecisionInstanceDto();
        instanceDto.getInputs().add(new InputInstanceDto(
          "id" + i, "input_id_" + i, "input_name_" + i, VariableType.STRING, "a" + i
        ));
        final OutputInstanceDto outputInstanceDto = new OutputInstanceDto();
        outputInstanceDto.setType(VariableType.SHORT);
        outputInstanceDto.setId("id" + i);
        outputInstanceDto.setClauseId("output_id_" + i);
        outputInstanceDto.setClauseName("output_name_" + i);
        outputInstanceDto.setValue("" + i);
        instanceDto.getOutputs().add(outputInstanceDto);

        return instanceDto;
      })
      .collect(Collectors.toList());

    final SearchResponse searchResponse = createSearchResponseMock(
      rawDataLimit.intValue(), actualInstanceCount, decisionInstances
    );

    // when
    final RawDataDecisionReportResultDto result = mapper.mapFrom(searchResponse, objectMapper);

    // then
    assertThat(result.getData().size(), is(rawDataLimit.intValue()));
    assertThat(result.getDecisionInstanceCount(), is(actualInstanceCount));
    IntStream.range(0, rawDataLimit.intValue())
      .forEach(i -> {
        assertThat(result.getData().get(i).getInputVariables().size(), is(rawDataLimit.intValue()));
        assertThat(result.getData().get(i).getOutputVariables().size(), is(rawDataLimit.intValue()));
      });
  }

  private SearchResponse createSearchResponseMock(final Integer hitCount, final Long totalCount) {
    return createSearchResponseMock(hitCount, totalCount, Collections.emptyList());
  }

  private SearchResponse createSearchResponseMock(final Integer hitCount,
                                                  final Long totalCount,
                                                  final List<DecisionInstanceDto> hitsData) {
    final SearchHits searchHits = mock(SearchHits.class);
    when(searchHits.getTotalHits()).thenReturn(totalCount);
    final SearchHit[] mockedHits = IntStream.range(0, hitCount)
      .mapToObj(i -> {
        final SearchHit searchHit = mock(SearchHit.class);
        String hitJson = "{}";
        if (hitsData.size() > i) {
          try {
            hitJson = objectMapper.writeValueAsString(hitsData.get(i));
          } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed serializing test hit");
          }
        }
        when(searchHit.getSourceAsString()).thenReturn(hitJson);
        return searchHit;
      })
      .toArray(SearchHit[]::new);
    when(searchHits.getHits()).thenReturn(mockedHits);

    final SearchResponse searchResponse = mock(SearchResponse.class);
    when(searchResponse.getHits()).thenReturn(searchHits);

    return searchResponse;
  }
}
