/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.*;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.ProcessInstanceFilter.Builder;
import io.camunda.service.search.filter.ProcessInstanceVariableFilter;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedCamundaSearchClient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessInstanceFilterTest {

  private ProcessInstanceServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new ProcessInstanceSearchQueryStub().registerWith(client);
    services = new ProcessInstanceServices(null, client);
  }

  @Test
  public void shouldQueryWhenEmpty() {
    // given
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f);
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldQueryByJoinRelation() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(
            f ->
                f.running(true)
                    .active(true)
                    .incidents(true)
                    .finished(true)
                    .completed(true)
                    .canceled(true));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchTermQuery.class);
    assertThat(((SearchTermQuery) queryVariant).field()).isEqualTo("joinRelation");
    assertThat(((SearchTermQuery) queryVariant).value().stringValue()).isEqualTo("processInstance");
  }

  @Test
  public void shouldQueryByActiveAndIncidents() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.running(true).active(true).incidents(true));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            searchBoolQuery -> {
              assertThat(searchBoolQuery.mustNot()).hasSize(1);
              assertThat(searchBoolQuery.mustNot().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchExistsQuery.class,
                      searchExistsQuery -> {
                        assertThat(searchExistsQuery.field()).isEqualTo("endDate");
                      });
            });
  }

  @Test
  public void shouldQueryByCompletedAndCanceled() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.finished(true).completed(true).canceled(true));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchExistsQuery.class,
            searchExistsQuery -> {
              assertThat(searchExistsQuery.field()).isEqualTo("endDate");
            });
  }

  @Test
  public void shouldQueryByRetriesLeft() {
    // given
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f.retriesLeft(true));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(SearchMatchNoneQuery.class, searchMatchNoneQuery -> {});

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchHasChildQuery.class,
            (searchHasChildQuery) -> {
              assertThat(searchHasChildQuery.type()).isEqualTo("activity");
              assertThat(searchHasChildQuery.query().queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (searchTermQuery) -> {
                        assertThat(searchTermQuery.field()).isEqualTo("jobFailedWithRetriesLeft");
                        assertThat(searchTermQuery.value().booleanValue()).isTrue();
                      });
            });
  }

  @Test
  public void shouldQueryByErrorMessage() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.errorMessage("not_found"));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(SearchMatchNoneQuery.class, searchMatchNoneQuery -> {});

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchHasChildQuery.class,
            (searchHasChildQuery) -> {
              assertThat(searchHasChildQuery.type()).isEqualTo("activity");
              assertThat(searchHasChildQuery.query().queryOption())
                  .isInstanceOfSatisfying(
                      SearchMatchQuery.class,
                      (searchTermQuery) -> {
                        assertThat(searchTermQuery.field()).isEqualTo("errorMessage");
                        assertThat(searchTermQuery.query()).isEqualTo("not_found");
                        assertThat(searchTermQuery.operator())
                            .isEqualTo(SearchMatchQuery.SearchMatchQueryOperator.AND);
                      });
            });
  }

  @Test
  public void shouldQueryByActivityId() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(
            f ->
                f.running(true)
                    .active(true)
                    .incidents(true)
                    .finished(true)
                    .completed(true)
                    .canceled(true)
                    .activityId("act"));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            searchBoolQuery -> {
              assertThat(searchBoolQuery.should()).hasSize(4);

              assertThat(searchBoolQuery.should().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchHasChildQuery.class,
                      (searchHasChildQuery) -> {
                        assertThat(searchHasChildQuery.type()).isEqualTo("activity");
                        assertThat(searchHasChildQuery.query().queryOption())
                            .isInstanceOfSatisfying(
                                SearchBoolQuery.class,
                                searchBoolQuery1 -> {
                                  assertThat(searchBoolQuery1.must()).hasSize(2);
                                  assertThat(searchBoolQuery1.must().get(0).queryOption())
                                      .isInstanceOfSatisfying(
                                          SearchTermQuery.class,
                                          (searchTermQuery) -> {
                                            assertThat(searchTermQuery.field())
                                                .isEqualTo("activityState");
                                            assertThat(searchTermQuery.value().stringValue())
                                                .isEqualTo("ACTIVE");
                                          });
                                  assertThat(searchBoolQuery1.must().get(1).queryOption())
                                      .isInstanceOfSatisfying(
                                          SearchTermQuery.class,
                                          (searchTermQuery) -> {
                                            assertThat(searchTermQuery.field())
                                                .isEqualTo("activityId");
                                            assertThat(searchTermQuery.value().stringValue())
                                                .isEqualTo("act");
                                          });
                                });
                      });

              assertThat(searchBoolQuery.should().get(1).queryOption())
                  .isInstanceOfSatisfying(
                      SearchHasChildQuery.class,
                      (searchHasChildQuery) -> {
                        assertThat(searchHasChildQuery.type()).isEqualTo("activity");
                        assertThat(searchHasChildQuery.query().queryOption())
                            .isInstanceOfSatisfying(
                                SearchBoolQuery.class,
                                searchBoolQuery1 -> {
                                  assertThat(searchBoolQuery1.must()).hasSize(3);
                                  assertThat(searchBoolQuery1.must().get(0).queryOption())
                                      .isInstanceOfSatisfying(
                                          SearchTermQuery.class,
                                          (searchTermQuery) -> {
                                            assertThat(searchTermQuery.field())
                                                .isEqualTo("activityState");
                                            assertThat(searchTermQuery.value().stringValue())
                                                .isEqualTo("ACTIVE");
                                          });
                                  assertThat(searchBoolQuery1.must().get(1).queryOption())
                                      .isInstanceOfSatisfying(
                                          SearchTermQuery.class,
                                          (searchTermQuery) -> {
                                            assertThat(searchTermQuery.field())
                                                .isEqualTo("activityId");
                                            assertThat(searchTermQuery.value().stringValue())
                                                .isEqualTo("act");
                                          });
                                  assertThat(searchBoolQuery1.must().get(2).queryOption())
                                      .isInstanceOfSatisfying(
                                          SearchExistsQuery.class,
                                          searchExistsQuery -> {
                                            assertThat(searchExistsQuery.field())
                                                .isEqualTo("errorMessage");
                                          });
                                });
                      });

              assertThat(searchBoolQuery.should().get(2).queryOption())
                  .isInstanceOfSatisfying(
                      SearchHasChildQuery.class,
                      (searchHasChildQuery) -> {
                        assertThat(searchHasChildQuery.type()).isEqualTo("activity");
                        assertThat(searchHasChildQuery.query().queryOption())
                            .isInstanceOfSatisfying(
                                SearchBoolQuery.class,
                                searchBoolQuery1 -> {
                                  assertThat(searchBoolQuery1.must()).hasSize(3);
                                  assertThat(searchBoolQuery1.must().get(0).queryOption())
                                      .isInstanceOfSatisfying(
                                          SearchTermQuery.class,
                                          (searchTermQuery) -> {
                                            assertThat(searchTermQuery.field())
                                                .isEqualTo("activityState");
                                            assertThat(searchTermQuery.value().stringValue())
                                                .isEqualTo("COMPLETED");
                                          });
                                  assertThat(searchBoolQuery1.must().get(1).queryOption())
                                      .isInstanceOfSatisfying(
                                          SearchTermQuery.class,
                                          (searchTermQuery) -> {
                                            assertThat(searchTermQuery.field())
                                                .isEqualTo("activityId");
                                            assertThat(searchTermQuery.value().stringValue())
                                                .isEqualTo("act");
                                          });
                                  assertThat(searchBoolQuery1.must().get(2).queryOption())
                                      .isInstanceOfSatisfying(
                                          SearchTermQuery.class,
                                          (searchTermQuery) -> {
                                            assertThat(searchTermQuery.field())
                                                .isEqualTo("activityType");
                                            assertThat(searchTermQuery.value().stringValue())
                                                .isEqualTo("END_EVENT");
                                          });
                                });
                      });

              assertThat(searchBoolQuery.should().get(3).queryOption())
                  .isInstanceOfSatisfying(
                      SearchHasChildQuery.class,
                      (searchHasChildQuery) -> {
                        assertThat(searchHasChildQuery.type()).isEqualTo("activity");
                        assertThat(searchHasChildQuery.query().queryOption())
                            .isInstanceOfSatisfying(
                                SearchBoolQuery.class,
                                searchBoolQuery1 -> {
                                  assertThat(searchBoolQuery1.must()).hasSize(2);
                                  assertThat(searchBoolQuery1.must().get(0).queryOption())
                                      .isInstanceOfSatisfying(
                                          SearchTermQuery.class,
                                          (searchTermQuery) -> {
                                            assertThat(searchTermQuery.field())
                                                .isEqualTo("activityState");
                                            assertThat(searchTermQuery.value().stringValue())
                                                .isEqualTo("TERMINATED");
                                          });
                                  assertThat(searchBoolQuery1.must().get(1).queryOption())
                                      .isInstanceOfSatisfying(
                                          SearchTermQuery.class,
                                          (searchTermQuery) -> {
                                            assertThat(searchTermQuery.field())
                                                .isEqualTo("activityId");
                                            assertThat(searchTermQuery.value().stringValue())
                                                .isEqualTo("act");
                                          });
                                });
                      });
            });
  }

  @Test
  public void shouldQueryByStartDateAndEndDate() {
    // given
    final var dateAfter = OffsetDateTime.of(2024, 3, 12, 10, 30, 15, 0, ZoneOffset.UTC);
    final var dateBefore = OffsetDateTime.of(2024, 7, 15, 10, 30, 15, 0, ZoneOffset.UTC);
    final var startDateFilter =
        FilterBuilders.dateValue((d) -> d.after(dateAfter).before(dateBefore));
    final var endDateFilter =
        FilterBuilders.dateValue((d) -> d.after(dateAfter).before(dateBefore));
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.startDate(startDateFilter).endDate(endDateFilter));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(4);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(SearchMatchNoneQuery.class, searchMatchNoneQuery -> {});

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            (searchRangeQuery) -> {
              assertThat(searchRangeQuery.field()).isEqualTo("startDate");
              assertThat(searchRangeQuery.gte()).isEqualTo("2024-03-12T10:30:15.000+0000");
              assertThat(searchRangeQuery.lt()).isEqualTo("2024-07-15T10:30:15.000+0000");
              assertThat(searchRangeQuery.format()).isEqualTo("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(3).queryOption())
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            (searchRangeQuery) -> {
              assertThat(searchRangeQuery.field()).isEqualTo("endDate");
              assertThat(searchRangeQuery.gte()).isEqualTo("2024-03-12T10:30:15.000+0000");
              assertThat(searchRangeQuery.lt()).isEqualTo("2024-07-15T10:30:15.000+0000");
              assertThat(searchRangeQuery.format()).isEqualTo("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
            });
  }

  @Test
  public void shouldQueryByBpmnProcessId() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.bpmnProcessIds("demoProcess"));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(SearchMatchNoneQuery.class, searchMatchNoneQuery -> {});

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("bpmnProcessId");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("demoProcess");
            });
  }

  @Test
  public void shouldQueryByProcessDefinitionVersion() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.processDefinitionVersions(5));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(SearchMatchNoneQuery.class, searchMatchNoneQuery -> {});

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("processVersion");
              assertThat(searchTermQuery.value().intValue()).isEqualTo(5);
            });
  }

  @Test
  public void shouldQueryByVariable() {
    // given
    final ProcessInstanceVariableFilter variableFilter =
        new ProcessInstanceVariableFilter.Builder().name("v1").values("23").build();
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.variable(variableFilter));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(SearchMatchNoneQuery.class, searchMatchNoneQuery -> {});

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchHasChildQuery.class,
            (searchHasChildQuery) -> {
              assertThat(searchHasChildQuery.type()).isEqualTo("variable");
              assertThat(searchHasChildQuery.query().queryOption())
                  .isInstanceOfSatisfying(
                      SearchBoolQuery.class,
                      searchBoolQuery -> {
                        assertThat(searchBoolQuery.must()).hasSize(2);
                        assertThat(searchBoolQuery.must().get(0).queryOption())
                            .isInstanceOfSatisfying(
                                SearchTermQuery.class,
                                (searchTermQuery) -> {
                                  assertThat(searchTermQuery.field()).isEqualTo("varName");
                                  assertThat(searchTermQuery.value().stringValue()).isEqualTo("v1");
                                });
                        assertThat(searchBoolQuery.must().get(1).queryOption())
                            .isInstanceOfSatisfying(
                                SearchTermQuery.class,
                                (searchTermQuery) -> {
                                  assertThat(searchTermQuery.field()).isEqualTo("varValue");
                                  assertThat(searchTermQuery.value().stringValue()).isEqualTo("23");
                                });
                      });
            });
  }

  @Test
  public void shouldQueryByBatchOperationId() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.batchOperationIds("abc"));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(SearchMatchNoneQuery.class, searchMatchNoneQuery -> {});

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("batchOperationIds");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("abc");
            });
  }

  @Test
  public void shouldQueryByParentProcessInstanceKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.parentProcessInstanceKeys(123L));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(SearchMatchNoneQuery.class, searchMatchNoneQuery -> {});

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("parentProcessInstanceKey");
              assertThat(searchTermQuery.value().longValue()).isEqualTo(123L);
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f.tenantIds("default"));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("joinRelation");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("processInstance");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(SearchMatchNoneQuery.class, searchMatchNoneQuery -> {});

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo("tenantId");
              assertThat(searchTermQuery.value().stringValue()).isEqualTo("default");
            });
  }

  @Test
  public void shouldReturnProcessInstance() {
    // given
    final ProcessInstanceQuery searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery().build();

    // when
    final SearchQueryResult<ProcessInstanceEntity> searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);
    final ProcessInstanceEntity item = searchQueryResult.items().get(0);
    assertThat(item.key()).isEqualTo(123L);
  }

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var processInstanceFilter = new Builder().build();

    // then
    assertThat(processInstanceFilter.running()).isFalse();
    assertThat(processInstanceFilter.active()).isFalse();
    assertThat(processInstanceFilter.incidents()).isFalse();
    assertThat(processInstanceFilter.finished()).isFalse();
    assertThat(processInstanceFilter.completed()).isFalse();
    assertThat(processInstanceFilter.canceled()).isFalse();
    assertThat(processInstanceFilter.retriesLeft()).isFalse();
    assertThat(processInstanceFilter.errorMessage()).isNull();
    assertThat(processInstanceFilter.activityId()).isNull();
    assertThat(processInstanceFilter.startDate()).isNull();
    assertThat(processInstanceFilter.endDate()).isNull();
    assertThat(processInstanceFilter.bpmnProcessIds()).isEmpty();
    assertThat(processInstanceFilter.processDefinitionVersions()).isEmpty();
    assertThat(processInstanceFilter.variable()).isNull();
    assertThat(processInstanceFilter.batchOperationIds()).isEmpty();
    assertThat(processInstanceFilter.parentProcessInstanceKeys()).isEmpty();
    assertThat(processInstanceFilter.tenantIds()).isEmpty();
  }
}
