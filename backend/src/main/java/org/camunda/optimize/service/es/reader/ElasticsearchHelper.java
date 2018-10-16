package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// TODO minor: evaluate making this a service to be injected instead of the plain Client
public class ElasticsearchHelper {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchHelper.class);

  private ElasticsearchHelper() {
    // noop
  }

  public static <T> List<T> retrieveAllScrollResults(final SearchResponse initialScrollResponse,
                                                     final Class<T> itemClass,
                                                     final ObjectMapper objectMapper,
                                                     final Client esclient,
                                                     final Integer scrollingTimeout) {
    final List<T> results = new ArrayList<>();

    SearchResponse currentScrollResp = initialScrollResponse;
    do {
      results.addAll(mapHits(currentScrollResp.getHits(), itemClass, objectMapper));

      if (currentScrollResp.getHits().getTotalHits() > results.size()) {
        currentScrollResp = esclient
          .prepareSearchScroll(currentScrollResp.getScrollId())
          .setScroll(new TimeValue(scrollingTimeout))
          .get();
      } else {
        currentScrollResp = null;
      }
    } while (currentScrollResp != null && currentScrollResp.getHits().getHits().length != 0);

    return results;
  }

  public static <T> List<T> mapHits(SearchHits searchHits, Class<T> itemClass, ObjectMapper objectMapper) {
    final List<T> results = new ArrayList<>();
    for (SearchHit hit : searchHits.getHits()) {
      String responseAsString = hit.getSourceAsString();
      try {
        T report = objectMapper.readValue(responseAsString, itemClass);
        results.add(report);
      } catch (IOException e) {
        String reason = "While mapping search results to class {} "
          + "it was not possible to deserialize a hit from Elasticsearch!"
          + " Hit response from Elasticsearch: "
          + responseAsString;
        logger.error(reason, itemClass.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return results;
  }
}
