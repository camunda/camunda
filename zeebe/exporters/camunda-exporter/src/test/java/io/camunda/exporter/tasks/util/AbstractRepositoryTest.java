/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class AbstractRepositoryTest {

  static Stream<Arguments> filtersAndQueries() {
    return Stream.of(
        Arguments.of(
            FilterByKeysQuery.of("key", List.of("1", "2", "3")),
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
            FilterByKeysQuery.of(
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
            FilterByKeysQuery.of("key", List.of("4", "5")).inclusion("joinRelation", "variable"),
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
            FilterByKeysQuery.of("key", List.of("4", "5"))
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
            }"""),
        // check with long key values
        Arguments.of(
            FilterByKeysQuery.of("key", List.of(1L, 2L))
                .exclusion("joinRelation", "processInstance"),
            """
            {
              "bool" : {
                "filter" : [ {
                  "bool" : {
                    "minimum_should_match" : "1",
                    "should" : [ {
                      "terms" : {
                        "key" : [ 1, 2 ]
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
            }"""),
        // check with int key values
        Arguments.of(
            FilterByKeysQuery.of("key", List.of(6, 7)).exclusion("joinRelation", "processInstance"),
            """
            {
              "bool" : {
                "filter" : [ {
                  "bool" : {
                    "minimum_should_match" : "1",
                    "should" : [ {
                      "terms" : {
                        "key" : [ 6, 7 ]
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
