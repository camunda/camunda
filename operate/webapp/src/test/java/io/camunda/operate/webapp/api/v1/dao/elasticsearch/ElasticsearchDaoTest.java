package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
    assertThrows(IllegalArgumentException.class, () -> elasticsearchDao.getLong("not a number"));
  }
}
