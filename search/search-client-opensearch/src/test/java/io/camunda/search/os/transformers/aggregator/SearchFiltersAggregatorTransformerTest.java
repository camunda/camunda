/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.aggregator.SearchAggregatorBuilders;
import io.camunda.search.clients.aggregator.SearchFiltersAggregator;
import io.camunda.search.clients.query.SearchQueryBuilders;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SearchFiltersAggregatorTransformerTest
    extends AbstractSearchAggregatorTransformerTest<SearchFiltersAggregator> {

  public static final String EXPECTED1 =
      """
    {
      "filters": {
        "filters": {
          "q1": {
            "term": {
              "key": {
                "value": 123
              }
            }
          },
          "q2": {
            "range": {
              "val": {
                "gt": 5
              }
            }
          }
        }
      }
    }
    """
          .replaceAll("\\s+", "");

  public static final String EXPECTED2 =
      """
    {
      "aggregations": {
        "filtersSubAgg": {
          "filters": {
            "filters": {
              "subQ": {
                "range": {
                  "val": {
                    "gt": 5
                  }
                }
              }
            }
          }
        }
      },
      "filters": {
        "filters": {
          "q1": {
            "term": {
              "key": {
                "value": 123
              }
            }
          },
          "q2": {
            "range": {
              "val": {
                "gt": 5
              }
            }
          }
        }
      }
    }
    """
          .replaceAll("\\s+", "");

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            SearchAggregatorBuilders.filters()
                .name("name")
                .namedQuery("q1", SearchQueryBuilders.term("key", 123L))
                .namedQuery("q2", SearchQueryBuilders.gt("val", 5))
                .build(),
            EXPECTED1),
        Arguments.arguments(
            SearchAggregatorBuilders.filters()
                .name("filtersAgg")
                .namedQuery("q1", SearchQueryBuilders.term("key", 123L))
                .namedQuery("q2", SearchQueryBuilders.gt("val", 5))
                .aggregations(
                    SearchAggregatorBuilders.filters()
                        .name("filtersSubAgg")
                        .namedQuery("subQ", SearchQueryBuilders.gt("val", 5))
                        .build())
                .build(),
            EXPECTED2));
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    // when - then
    assertThatThrownBy(() -> SearchAggregatorBuilders.filters().build())
        .hasMessageContaining("Expected non-null field for name.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullQueries() {
    // when/then
    assertThatThrownBy(() -> SearchAggregatorBuilders.filters().name("name").build())
        .hasMessageContaining("Expected non-null field for queries.")
        .isInstanceOf(NullPointerException.class);
  }

  @Override
  protected Class<SearchFiltersAggregator> getTransformerClass() {
    return SearchFiltersAggregator.class;
  }
}
