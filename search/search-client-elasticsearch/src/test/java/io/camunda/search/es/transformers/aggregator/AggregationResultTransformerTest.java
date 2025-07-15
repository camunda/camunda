/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpParser;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AggregationResultTransformerTest {

  public static final String RESPONSE_BASE =
      """
  {
    "took": 5,
    "timed_out": false,
    "_shards": {
      "total": 1,
      "successful": 1,
      "skipped": 0,
      "failed": 0
    },
    "hits": {
      "hits": []
    },
    "aggregations": %s
  }
  """;
  private final SearchAggregationResultTransformer transformer =
      new SearchAggregationResultTransformer(null, null);

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        // single aggregation
        Arguments.arguments("{'children#agg1': {'doc_count': 4}}", "{'agg1':{'docCount':4}}"),
        // two aggregations
        Arguments.arguments(
            "{'filter#agg1': {'doc_count': 4}, 'children#agg2': {'doc_count': 4}}",
            "{'agg2':{'docCount':4},'agg1':{'docCount':4}}"),
        // nested aggregations
        Arguments.arguments(
            "{'filter#agg1': {'doc_count': 4, 'children#agg2': {'doc_count': 4}}}",
            "{'agg1':{'docCount':4,'aggregations':{'agg2':{'docCount':4}}}}"),
        // multiple nested aggregations
        Arguments.arguments(
            """
            {
                "children#to-flow-nodes": {
                  "doc_count": 4,
                  "filter#filter-flow-nodes": {
                    "doc_count": 2,
                    "sterms#group-flow-nodes": {
                      "doc_count_error_upper_bound": 0,
                      "sum_other_doc_count": 0,
                      "buckets": [
                        {
                          "key": "EndEvent",
                          "doc_count": 2,
                          "filters#group-filters": {
                            "buckets": {
                              "active": {
                                "doc_count": 0
                              },
                              "canceled": {
                                "doc_count": 0
                              },
                              "completed": {
                                "doc_count": 2
                              },
                              "incidents": {
                                "doc_count": 0
                              }
                            }
                          }
                        }
                      ]
                    }
                  }
                }
              }
            """,
            """
            {
              "to-flow-nodes": {
                "docCount": 4,
                "aggregations": {
                  "filter-flow-nodes": {
                    "docCount": 2,
                    "aggregations": {
                      "group-flow-nodes": {
                        "aggregations": {
                          "EndEvent": {
                            "docCount": 2,
                            "aggregations": {
                              "group-filters": {
                                "aggregations": {
                                  "canceled": {
                                    "docCount": 0
                                  },
                                  "incidents": {
                                    "docCount": 0
                                  },
                                  "active": {
                                    "docCount": 0
                                  },
                                  "completed": {
                                    "docCount": 2
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """));
  }

  @ParameterizedTest
  @MethodSource("provideAggregations")
  public void shouldThrowErrorOnNullType(
      final String responseAggsString, final String expectedResultString) throws IOException {
    // given
    final var objectMapper =
        new ObjectMapper()
            .configure(Feature.ALLOW_SINGLE_QUOTES, true)
            .setSerializationInclusion(Include.NON_NULL);
    final var jsonpMapper = new JacksonJsonpMapper(objectMapper);
    final var jsonParser = objectMapper.createParser(RESPONSE_BASE.formatted(responseAggsString));
    final var jsonpParser = new JacksonJsonpParser(jsonParser, jsonpMapper);
    final var searchResponse = SearchResponse._DESERIALIZER.deserialize(jsonpParser, jsonpMapper);

    // when
    final var result = transformer.apply(searchResponse.aggregations());

    // then
    final var resultString = objectMapper.writeValueAsString(result);
    Assertions.assertThat(resultString.replaceAll("\\s+", ""))
        .isEqualTo(expectedResultString.replaceAll("\\s+", "").replace("'", "\""));
  }
}
