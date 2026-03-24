/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.operate.webapp.api.v1.entities.Query;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;

class ElasticsearchDaoTest {

  ElasticsearchDao<Object> elasticsearchDao =
      new ElasticsearchDao<Object>() {
        @Override
        protected void buildFiltering(
            final Query<Object> query, final SearchSourceBuilder searchSourceBuilder) {}
      };

  @Test
  void shouldGetLongFromInteger() {
    assertThat(elasticsearchDao.getLong(1)).isEqualTo(1L);
  }

  @Test
  void shouldGetLongFromLong() {
    assertThat(elasticsearchDao.getLong(1L)).isEqualTo(1L);
  }

  @Test
  void shouldGetNullFromNull() {
    assertThat(elasticsearchDao.getLong(null)).isNull();
  }

  @Test
  void shouldThrowExceptionForNonNumeric() {
    assertThatThrownBy(() -> elasticsearchDao.getLong("not a number"))
        .isInstanceOf(NumberFormatException.class);
  }
}
