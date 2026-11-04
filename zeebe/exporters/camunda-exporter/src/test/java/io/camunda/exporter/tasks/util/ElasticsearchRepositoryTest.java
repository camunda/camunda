/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ElasticsearchRepositoryTest {
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
          .enable(SerializationFeature.INDENT_OUTPUT);
  private final JacksonJsonpMapper jacksonJsonpMapper = new JacksonJsonpMapper();

  @ParameterizedTest
  @MethodSource("filtersAndQueries")
  void shouldConvertFilterToQuery(final FilterByKeysQuery filter, final String expectedJsonQuery)
      throws JsonProcessingException {
    // given/when
    final var query = ElasticsearchRepository.toQuery(filter);

    // then
    final String json = toNormalisedJson(JsonpUtils.toJsonString(query, jacksonJsonpMapper));
    assertThat(json).isEqualTo(expectedJsonQuery);
  }

  private String toNormalisedJson(final String json) throws JsonProcessingException {
    return objectMapper.writeValueAsString(objectMapper.readValue(json, Map.class));
  }

  static Stream<Arguments> filtersAndQueries() {
    return Stream.of(
        Arguments.of(
            new FilterByKeysQuery("key", List.of("1", "2", "3")),
            """
            {
              "bool" : {
                "filter" : [ {
                  "bool" : {
                    "minimum_should_match" : "1",
                    "should" : [ {
                      "terms" : {
                        "key" : [ "1", "2", "3" ]
                      }
                    } ]
                  }
                } ]
              }
            }"""),
        Arguments.of(
            new FilterByKeysQuery(
                // using immutable map to ensure sort order is consistent for the test
                ImmutableMap.of("piKey", List.of("1", "2", "3"), "rpiKey", List.of("1", "4", "5"))),
            """
            {
              "bool" : {
                "filter" : [ {
                  "bool" : {
                    "minimum_should_match" : "1",
                    "should" : [ {
                      "terms" : {
                        "piKey" : [ "1", "2", "3" ]
                      }
                    }, {
                      "terms" : {
                        "rpiKey" : [ "1", "4", "5" ]
                      }
                    } ]
                  }
                } ]
              }
            }"""),
        Arguments.of(
            new FilterByKeysQuery("key", List.of("4", "5")).inclusion("joinRelation", "variable"),
            """
            {
              "bool" : {
                "filter" : [ {
                  "bool" : {
                    "minimum_should_match" : "1",
                    "should" : [ {
                      "terms" : {
                        "key" : [ "4", "5" ]
                      }
                    } ]
                  }
                }, {
                  "term" : {
                    "joinRelation" : {
                      "value" : "variable"
                    }
                  }
                } ]
              }
            }"""),
        Arguments.of(
            new FilterByKeysQuery("key", List.of("4", "5"))
                .exclusion("joinRelation", "processInstance"),
            """
            {
              "bool" : {
                "filter" : [ {
                  "bool" : {
                    "minimum_should_match" : "1",
                    "should" : [ {
                      "terms" : {
                        "key" : [ "4", "5" ]
                      }
                    } ]
                  }
                } ],
                "must_not" : [ {
                  "term" : {
                    "joinRelation" : {
                      "value" : "processInstance"
                    }
                  }
                } ]
              }
            }"""));
  }
}
