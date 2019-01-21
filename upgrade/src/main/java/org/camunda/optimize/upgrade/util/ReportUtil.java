package org.camunda.optimize.upgrade.util;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.service.es.schema.type.report.SingleProcessReportType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;

public class ReportUtil {
  private ReportUtil() {
  }

  public static Map<String, Map> buildSingleReportIdToVisualizationAndViewMap(ConfigurationService configurationService) {
    RestHighLevelClient client = ElasticsearchHighLevelRestClientBuilder.build(configurationService);
    try {
      final SearchResponse searchResponse = client.search(
        new SearchRequest(getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE)),
        RequestOptions.DEFAULT
      );

      return Arrays.stream(searchResponse.getHits().getHits())
        .map(doc -> {
          final Map<String, Object> sourceAsMap = doc.getSourceAsMap();

          final Map<String, Object> resultMap = new HashMap<>();
          Optional.ofNullable(((Map) sourceAsMap.get(SingleProcessReportType.DATA)).get("visualization"))
            .ifPresent(o -> resultMap.put("visualization", o));
          Optional.ofNullable(((Map) sourceAsMap.get(SingleProcessReportType.DATA)).get("view"))
            .ifPresent(o -> resultMap.put("view", o));

          return new SimpleImmutableEntry<>(doc.getId(), ImmutableMap.copyOf(resultMap));
        })
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    } catch (IOException e) {
      String errorMessage = "Could not retrieve all single reports to migrate combined reports!";
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }
}
