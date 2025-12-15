package io.camunda.client.incident;

import static io.camunda.client.util.assertions.SortAssert.assertSort;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.IncidentStatisticsByErrorHashCodeQuery;
import io.camunda.client.protocol.rest.IncidentStatisticsByErrorHashCodeQueryResult;
import io.camunda.client.protocol.rest.IncidentStatisticsByErrorHashCodeResult;
import io.camunda.client.protocol.rest.OffsetPagination;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IncidentStatisticsTest extends ClientRestTest {

  private static final String ERROR_HASH_CODE = "1234567890";

  @BeforeEach
  void setup() {
    gatewayService.onIncidentStatisticsByErrorHashCodeRequest(
        ERROR_HASH_CODE,
        Instancio.create(IncidentStatisticsByErrorHashCodeQueryResult.class)
            .items(getIncidentStatisticsByErrorHashCodeResults()));
  }

  @Test
  void shouldRetrieveIncidentStatisticsByErrorHashCode() throws JsonProcessingException {
    // when
    client.newIncidentStatisticsByErrorHashCodeRequest(ERROR_HASH_CODE).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/incidents/" + ERROR_HASH_CODE + "/statistics");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode json = mapper.readTree(request.getBodyAsString());

    AssertionsForClassTypes.assertThat(json.get("page")).isNull();
    AssertionsForClassTypes.assertThat(json.get("sort")).isNotNull();
    AssertionsForClassTypes.assertThat(json.get("sort").isArray()).isTrue();
    Assertions.assertThat(json.get("sort")).isEmpty();
  }

  @Test
  void shouldRetrieveIncidentStatisticsByErrorHashCodeWithOffsetPagination() {
    // when
    client
        .newIncidentStatisticsByErrorHashCodeRequest(ERROR_HASH_CODE)
        .page(p -> p.from(5).limit(10))
        .send()
        .join();

    // then
    final IncidentStatisticsByErrorHashCodeQuery request =
        gatewayService.getLastRequest(IncidentStatisticsByErrorHashCodeQuery.class);

    final OffsetPagination page = request.getPage();
    AssertionsForClassTypes.assertThat(page).isNotNull();
    AssertionsForClassTypes.assertThat(page).extracting(OffsetPagination::getFrom).isEqualTo(5);

    AssertionsForClassTypes.assertThat(page).extracting(OffsetPagination::getLimit).isEqualTo(10);
  }

  @Test
  void shouldRetrieveIncidentStatisticsByErrorHashCodeWithSorting() {
    // when
    client
        .newIncidentStatisticsByErrorHashCodeRequest(ERROR_HASH_CODE)
        .sort(
            s ->
                s.processDefinitionName()
                    .asc()
                    .processDefinitionVersion()
                    .desc()
                    .tenantId()
                    .asc()
                    .activeInstancesWithErrorCount()
                    .desc())
        .send()
        .join();

    // then
    final IncidentStatisticsByErrorHashCodeQuery request =
        gatewayService.getLastRequest(IncidentStatisticsByErrorHashCodeQuery.class);

    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromIncidentStatisticsByErrorHashCodeQuerySortRequest(
            Objects.requireNonNull(request.getSort()));

    Assertions.assertThat(sorts).hasSize(4);
    assertSort(sorts.get(0), "processDefinitionName", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "processDefinitionVersion", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "tenantId", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "activeInstancesWithErrorCount", SortOrderEnum.DESC);
  }

  private static List<IncidentStatisticsByErrorHashCodeResult>
      getIncidentStatisticsByErrorHashCodeResults() {
    final IncidentStatisticsByErrorHashCodeResult item =
        Instancio.create(IncidentStatisticsByErrorHashCodeResult.class);
    item.setProcessDefinitionKey("12345");
    final List<IncidentStatisticsByErrorHashCodeResult> resultList = new ArrayList<>();
    resultList.add(item);
    return resultList;
  }
}
