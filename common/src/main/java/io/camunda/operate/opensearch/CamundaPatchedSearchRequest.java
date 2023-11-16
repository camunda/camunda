/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.camunda.operate.opensearch;

import jakarta.json.stream.JsonGenerator;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.ScriptField;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.mapping.RuntimeField;
import org.opensearch.client.opensearch._types.query_dsl.FieldAndFormat;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Rescore;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The purpose of this file is to workaround the design issues in the Opensearch java client library.
 * It contains the fixes for the following:
 *  - issue #5788 OpenSearch: Search_After Null Dates
 *
 *  Changes in code are marked with the issue number and description
 */

@JsonpDeserializable
public class CamundaPatchedSearchRequest extends RequestBase implements JsonpSerializable {
  private SearchRequest searchRequest;

  public CamundaPatchedSearchRequest(SearchRequest searchRequest) {
    this.searchRequest = searchRequest;
  }

  public static CamundaPatchedSearchRequest from(SearchRequest searchRequest) {
    return new CamundaPatchedSearchRequest(searchRequest);
  }

  public void serialize(JsonGenerator generator, JsonpMapper mapper) {
    generator.writeStartObject();
    serializeInternal(generator, mapper);
    generator.writeEnd();
  }

  protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

    if (searchRequest.source() != null) {
      generator.writeKey("_source");
      searchRequest.source().serialize(generator, mapper);

    }
    if (ApiTypeHelper.isDefined(searchRequest.aggregations())) {
      generator.writeKey("aggregations");
      generator.writeStartObject();
      for (Map.Entry<String, Aggregation> item0 : searchRequest.aggregations().entrySet()) {
        generator.writeKey(item0.getKey());
        item0.getValue().serialize(generator, mapper);

      }
      generator.writeEnd();

    }
    if (searchRequest.collapse() != null) {
      generator.writeKey("collapse");
      searchRequest.collapse().serialize(generator, mapper);

    }
    if (ApiTypeHelper.isDefined(searchRequest.docvalueFields())) {
      generator.writeKey("docvalue_fields");
      generator.writeStartArray();
      for (FieldAndFormat item0 : searchRequest.docvalueFields()) {
        item0.serialize(generator, mapper);

      }
      generator.writeEnd();

    }
    if (searchRequest.explain() != null) {
      generator.writeKey("explain");
      generator.write(searchRequest.explain());

    }
    if (ApiTypeHelper.isDefined(searchRequest.fields())) {
      generator.writeKey("fields");
      generator.writeStartArray();
      for (FieldAndFormat item0 : searchRequest.fields()) {
        item0.serialize(generator, mapper);

      }
      generator.writeEnd();

    }
    if (searchRequest.from() != null) {
      generator.writeKey("from");
      generator.write(searchRequest.from());

    }
    if (searchRequest.highlight() != null) {
      generator.writeKey("highlight");
      searchRequest.highlight().serialize(generator, mapper);

    }
    if (ApiTypeHelper.isDefined(searchRequest.indicesBoost())) {
      generator.writeKey("indices_boost");
      generator.writeStartArray();
      for (Map<String, Double> item0 : searchRequest.indicesBoost()) {
        generator.writeStartObject();
        if (item0 != null) {
          for (Map.Entry<String, Double> item1 : item0.entrySet()) {
            generator.writeKey(item1.getKey());
            generator.write(item1.getValue());

          }
        }
        generator.writeEnd();

      }
      generator.writeEnd();

    }
    if (searchRequest.minScore() != null) {
      generator.writeKey("min_score");
      generator.write(searchRequest.minScore());

    }

    if (searchRequest.pit() != null) {
      generator.writeKey("pit");
      searchRequest.pit().serialize(generator, mapper);
    }

    if (searchRequest.postFilter() != null) {
      generator.writeKey("post_filter");
      searchRequest.postFilter().serialize(generator, mapper);

    }
    if (searchRequest.profile() != null) {
      generator.writeKey("profile");
      generator.write(searchRequest.profile());

    }
    if (searchRequest.query() != null) {
      generator.writeKey("query");
      searchRequest.query().serialize(generator, mapper);

    }
    if (ApiTypeHelper.isDefined(searchRequest.rescore())) {
      generator.writeKey("rescore");
      generator.writeStartArray();
      for (Rescore item0 : searchRequest.rescore()) {
        item0.serialize(generator, mapper);

      }
      generator.writeEnd();

    }
    if (ApiTypeHelper.isDefined(searchRequest.runtimeMappings())) {
      generator.writeKey("runtime_mappings");
      generator.writeStartObject();
      for (Map.Entry<String, RuntimeField> item0 : searchRequest.runtimeMappings().entrySet()) {
        generator.writeKey(item0.getKey());
        item0.getValue().serialize(generator, mapper);

      }
      generator.writeEnd();

    }
    if (ApiTypeHelper.isDefined(searchRequest.scriptFields())) {
      generator.writeKey("script_fields");
      generator.writeStartObject();
      for (Map.Entry<String, ScriptField> item0 : searchRequest.scriptFields().entrySet()) {
        generator.writeKey(item0.getKey());
        item0.getValue().serialize(generator, mapper);

      }
      generator.writeEnd();

    }
    if (ApiTypeHelper.isDefined(searchRequest.searchAfter())) {
      generator.writeKey("search_after");
      generator.writeStartArray();
      for (String item0 : searchRequest.searchAfter()) {
        // [START] Workaround for issue #5788 OpenSearch: Search_After Null Dates
        if (item0.equals(String.valueOf(Long.MIN_VALUE))) {
          generator.write(Long.MIN_VALUE);
        } else {
          generator.write(item0);
        }
        // [END] Workaround for issue #5788 OpenSearch: Search_After Null Dates
        // Original line was:
        // generator.write(item0);
      }
      generator.writeEnd();

    }
    if (searchRequest.seqNoPrimaryTerm() != null) {
      generator.writeKey("seq_no_primary_term");
      generator.write(searchRequest.seqNoPrimaryTerm());

    }
    if (searchRequest.size() != null) {
      generator.writeKey("size");
      generator.write(searchRequest.size());

    }
    if (searchRequest.slice() != null) {
      generator.writeKey("slice");
      searchRequest.slice().serialize(generator, mapper);

    }
    if (ApiTypeHelper.isDefined(searchRequest.sort())) {
      generator.writeKey("sort");
      generator.writeStartArray();
      for (SortOptions item0 : searchRequest.sort()) {
        item0.serialize(generator, mapper);

      }
      generator.writeEnd();

    }
    if (ApiTypeHelper.isDefined(searchRequest.stats())) {
      generator.writeKey("stats");
      generator.writeStartArray();
      for (String item0 : searchRequest.stats()) {
        generator.write(item0);

      }
      generator.writeEnd();

    }
    if (ApiTypeHelper.isDefined(searchRequest.storedFields())) {
      generator.writeKey("stored_fields");
      generator.writeStartArray();
      for (String item0 : searchRequest.storedFields()) {
        generator.write(item0);

      }
      generator.writeEnd();

    }
    if (searchRequest.suggest() != null) {
      generator.writeKey("suggest");
      searchRequest.suggest().serialize(generator, mapper);

    }
    if (searchRequest.terminateAfter() != null) {
      generator.writeKey("terminate_after");
      generator.write(searchRequest.terminateAfter());

    }
    if (searchRequest.timeout() != null) {
      generator.writeKey("timeout");
      generator.write(searchRequest.timeout());

    }
    if (searchRequest.trackScores() != null) {
      generator.writeKey("track_scores");
      generator.write(searchRequest.trackScores());

    }
    if (searchRequest.trackTotalHits() != null) {
      generator.writeKey("track_total_hits");
      searchRequest.trackTotalHits().serialize(generator, mapper);

    }
    if (searchRequest.version() != null) {
      generator.writeKey("version");
      generator.write(searchRequest.version());

    }

  }

  public static final SimpleEndpoint<CamundaPatchedSearchRequest, ?> _ENDPOINT = new SimpleEndpoint<>(

    // Request method
    request -> {
      return "POST";

    },

    // Request path
    request -> {
      final int _index = 1 << 0;

      int propsSet = 0;

      if (ApiTypeHelper.isDefined(request.searchRequest.index()))
        propsSet |= _index;

      if (propsSet == 0) {
        StringBuilder buf = new StringBuilder();
        buf.append("/_search");
        return buf.toString();
      }
      if (propsSet == (_index)) {
        StringBuilder buf = new StringBuilder();
        buf.append("/");
        SimpleEndpoint.pathEncode(request.searchRequest.index().stream().map(v -> v).collect(Collectors.joining(",")), buf);
        buf.append("/_search");
        return buf.toString();
      }
      throw SimpleEndpoint.noPathTemplateFound("path");

    },

    // Request parameters
    request -> {
      Map<String, String> params = new HashMap<>();
      params.put("typed_keys", "true");
      if (request.searchRequest.df() != null) {
        params.put("df", request.searchRequest.df());
      }
      if (request.searchRequest.preFilterShardSize() != null) {
        params.put("pre_filter_shard_size", String.valueOf(request.searchRequest.preFilterShardSize()));
      }
      if (request.searchRequest.minCompatibleShardNode() != null) {
        params.put("min_compatible_shard_node", request.searchRequest.minCompatibleShardNode());
      }
      if (request.searchRequest.lenient() != null) {
        params.put("lenient", String.valueOf(request.searchRequest.lenient()));
      }
      if (request.searchRequest.routing() != null) {
        params.put("routing", request.searchRequest.routing());
      }
      if (request.searchRequest.ignoreUnavailable() != null) {
        params.put("ignore_unavailable", String.valueOf(request.searchRequest.ignoreUnavailable()));
      }
      if (request.searchRequest.allowNoIndices() != null) {
        params.put("allow_no_indices", String.valueOf(request.searchRequest.allowNoIndices()));
      }
      if (request.searchRequest.analyzer() != null) {
        params.put("analyzer", request.searchRequest.analyzer());
      }
      if (request.searchRequest.ignoreThrottled() != null) {
        params.put("ignore_throttled", String.valueOf(request.searchRequest.ignoreThrottled()));
      }
      if (request.searchRequest.maxConcurrentShardRequests() != null) {
        params.put("max_concurrent_shard_requests", String.valueOf(request.searchRequest.maxConcurrentShardRequests()));
      }
      if (request.searchRequest.allowPartialSearchResults() != null) {
        params.put("allow_partial_search_results", String.valueOf(request.searchRequest.allowPartialSearchResults()));
      }
      if (ApiTypeHelper.isDefined(request.searchRequest.expandWildcards())) {
        params.put("expand_wildcards",
          request.searchRequest.expandWildcards().stream()
            .map(v -> v.jsonValue()).collect(Collectors.joining(",")));
      }
      if (request.searchRequest.preference() != null) {
        params.put("preference", request.searchRequest.preference());
      }
      if (request.searchRequest.analyzeWildcard() != null) {
        params.put("analyze_wildcard", String.valueOf(request.searchRequest.analyzeWildcard()));
      }
      if (request.searchRequest.scroll() != null) {
        params.put("scroll", request.searchRequest.scroll()._toJsonString());
      }
      if (request.searchRequest.searchType() != null) {
        params.put("search_type", request.searchRequest.searchType().jsonValue());
      }
      if (request.searchRequest.ccsMinimizeRoundtrips() != null) {
        params.put("ccs_minimize_roundtrips", String.valueOf(request.searchRequest.ccsMinimizeRoundtrips()));
      }
      if (request.searchRequest.q() != null) {
        params.put("q", request.searchRequest.q());
      }
      if (request.searchRequest.defaultOperator() != null) {
        params.put("default_operator", request.searchRequest.defaultOperator().jsonValue());
      }
      if (request.searchRequest.requestCache() != null) {
        params.put("request_cache", String.valueOf(request.searchRequest.requestCache()));
      }
      if (request.searchRequest.batchedReduceSize() != null) {
        params.put("batched_reduce_size", String.valueOf(request.searchRequest.batchedReduceSize()));
      }
      return params;

    }, SimpleEndpoint.emptyMap(), true, SearchResponse._DESERIALIZER);

  /**
   * Create an "{@code search}" endpoint.
   */
  public static <TDocument> Endpoint<CamundaPatchedSearchRequest, SearchResponse<TDocument>, ErrorResponse> createSearchEndpoint(
    JsonpDeserializer<TDocument> tDocumentDeserializer) {
    return _ENDPOINT
      .withResponseDeserializer(SearchResponse.createSearchResponseDeserializer(tDocumentDeserializer));
  }
}
