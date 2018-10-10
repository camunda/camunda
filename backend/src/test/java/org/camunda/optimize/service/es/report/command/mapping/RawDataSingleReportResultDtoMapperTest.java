package org.camunda.optimize.service.es.report.command.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataSingleReportResultDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.Test;

import java.util.stream.IntStream;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RawDataSingleReportResultDtoMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testMapFromSearchResponse_hitCountNotEqualTotalCount() {
    // given
    Long rawDataLimit = 2L;
    Long actualInstanceCount = 3L;
    final RawDataSingleReportResultDtoMapper mapper = new RawDataSingleReportResultDtoMapper(rawDataLimit);
    final SearchResponse searchResponse = createSearchResponseMock(rawDataLimit.intValue(), actualInstanceCount);

    // when
    final RawDataSingleReportResultDto result = mapper.mapFrom(searchResponse, objectMapper);

    // then
    assertThat(result.getResult().size(), is(rawDataLimit.intValue()));
    assertThat(result.getProcessInstanceCount(), is(actualInstanceCount));
  }

  private SearchResponse createSearchResponseMock(final Integer hitCount, final Long totalCount) {
    final SearchHits searchHits = mock(SearchHits.class);
    when(searchHits.getTotalHits()).thenReturn(totalCount);
    final SearchHit[] mockedHits = IntStream.range(0, hitCount)
        .mapToObj(operand -> {
          final SearchHit searchHit = mock(SearchHit.class);
          // no actual data needed
          when(searchHit.getSourceAsString()).thenReturn("{}");
          return searchHit;
        })
        .toArray(SearchHit[]::new);
    when(searchHits.getHits()).thenReturn(mockedHits);

    final SearchResponse searchResponse = mock(SearchResponse.class);
    when(searchResponse.getHits()).thenReturn(searchHits);

    return searchResponse;
  }
}
