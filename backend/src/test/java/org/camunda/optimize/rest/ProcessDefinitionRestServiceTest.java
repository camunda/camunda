package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.DateFilterDto;
import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext.xml" })
public class ProcessDefinitionRestServiceTest extends AbstractJerseyTest {

  @Autowired
  private TransportClient esClientMock;

  @Autowired
  private ConfigurationService configurationService;

  /**
   * Make sure that serialization\deserialization is working properly for REST
   * services communication
   *
   * @throws Exception
   */
  @Test
  public void getHeatMap() throws Exception {
    //given
    Date targetDate = new Date();
    SimpleDateFormat format = new SimpleDateFormat(configurationService.getDateFormat());
    HeatMapQueryDto dto = prepareDto(targetDate);
    setUpElasticSearchMock(format.format(targetDate));

    //when
    Response response = target("process-definition")
        .path("heatmap")
        .request(MediaType.APPLICATION_JSON)
        .header("Content-Type", MediaType.APPLICATION_JSON)
        .post(Entity.json(dto));

    //then
    HashMap deserializedResult = response.readEntity(new GenericType<HashMap<String, Long>>(){});
    assertThat(deserializedResult.size(),is(2));
  }

  private void setUpElasticSearchMock(String expectedDate) {
    SearchRequestBuilder searchRequestMock = Mockito.mock(SearchRequestBuilder.class);
    Mockito
        .when(searchRequestMock.setQuery(queryContainsDate(expectedDate)))
        .thenReturn(searchRequestMock);

    Mockito
        .when(searchRequestMock.addAggregation(Mockito.any(AggregationBuilder.class)))
        .thenReturn(searchRequestMock);

    ListenableActionFuture<SearchResponse> futureResponse = setUpResponseObject();

    Mockito.when(searchRequestMock.execute()).thenReturn(futureResponse);

    Mockito.when(
        this.esClientMock.prepareSearch(configurationService.getOptimizeIndex())
            .setTypes(configurationService.getEventType())
    ).thenReturn(searchRequestMock);
  }

  private ListenableActionFuture<SearchResponse> setUpResponseObject() {
    ListenableActionFuture<SearchResponse> futureResponse = Mockito.mock(ListenableActionFuture.class);
    SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    Aggregations aggregations = Mockito.mock(Aggregations.class);
    Terms mockTerms = Mockito.mock(Terms.class);
    List<Terms.Bucket> mockBuckets = new ArrayList<>();
    Terms.Bucket mockBucket = Mockito.mock(Terms.Bucket.class);
    Mockito.when(mockBucket.getKeyAsString()).thenReturn("test");
    Mockito.when(mockBucket.getDocCount()).thenReturn(1L);
    mockBuckets.add(mockBucket);
    Mockito.when(mockTerms.getBuckets()).thenReturn(mockBuckets);
    Mockito.when(aggregations.get(Mockito.eq("activities"))).thenReturn(mockTerms);

    Cardinality mockCardinality = Mockito.mock(Cardinality.class);
    Mockito.when(mockCardinality.getValue()).thenReturn(1L);
    Mockito.when(aggregations.get(Mockito.eq("pi"))).thenReturn(mockCardinality);

    Mockito.when(mockResponse.getAggregations()).thenReturn(aggregations);
    Mockito.when(futureResponse.actionGet()).thenReturn(mockResponse);
    return futureResponse;
  }

  private HeatMapQueryDto prepareDto(Date targetDate) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId("test");
    FilterMapDto filter = new FilterMapDto();
    List<DateFilterDto> dates = new ArrayList<>();
    DateFilterDto date = new DateFilterDto();
    date.setOperator(">");
    date.setValue(targetDate);
    date.setType("start_date");
    dates.add(date);
    filter.setDates(dates);
    dto.setFilter(filter);
    return dto;
  }

  /**
   * Custom matcher for verifying actual and expected ValueObjects match.
   */
  static class BoolQueryBuilderMatcher implements ArgumentMatcher<BoolQueryBuilder> {

    private String expectedDate;

    public BoolQueryBuilderMatcher(String expectedDate) {
      this.expectedDate = expectedDate;
    }

    @Override
    public boolean matches(BoolQueryBuilder actual) {
      // could improve with null checks
      return actual.toString().contains(expectedDate);
    }
  }

  /**
   * Convenience factory method for using the custom ValueObject matcher.
   */
  static BoolQueryBuilder queryContainsDate(String expected) {
    return Mockito.argThat(new BoolQueryBuilderMatcher(expected));
  }

}