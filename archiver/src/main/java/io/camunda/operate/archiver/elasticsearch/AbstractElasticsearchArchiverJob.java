/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver.elasticsearch;

import io.camunda.operate.archiver.AbstractArchiverJob;
import io.camunda.operate.archiver.ArchiveBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.camunda.operate.util.ElasticsearchUtil;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractElasticsearchArchiverJob extends AbstractArchiverJob {
  @Autowired
  private RestHighLevelClient esClient;

  protected ArchiveBatch createArchiveBatch(SearchResponse searchResponse, String datesAggName, String instancesAggName) {
    final List<? extends Histogram.Bucket> buckets =
        ((Histogram) searchResponse.getAggregations().get(datesAggName))
            .getBuckets();

    if (buckets.size() > 0) {
      final Histogram.Bucket bucket = buckets.get(0);
      final String finishDate = bucket.getKeyAsString();
      SearchHits hits = ((TopHits)bucket.getAggregations().get(instancesAggName)).getHits();
      final ArrayList<Object> ids = Arrays.stream(hits.getHits())
          .collect(ArrayList::new, (list, hit) -> list.add(hit.getId()), (list1, list2) -> list1.addAll(list2));
      return new ArchiveBatch(finishDate, ids);
    } else {
      return null;
    }
  }

  protected CompletableFuture<SearchResponse> sendSearchRequest(final SearchRequest searchRequest) {
    return ElasticsearchUtil.searchAsync(searchRequest, archiverExecutor, esClient);
  }
}
