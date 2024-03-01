/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.operate.util.ConversionUtils.stringIsEmpty;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import java.util.List;
import java.util.stream.Collectors;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class ElasticsearchDao<T> {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  @Qualifier("esClient")
  protected RestHighLevelClient elasticsearch;

  @Autowired protected TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired protected ObjectMapper objectMapper;

  @Autowired protected OperateDateTimeFormatter dateTimeFormatter;

  protected void buildSorting(
      final Query<T> query,
      final String uniqueSortKey,
      final SearchSourceBuilder searchSourceBuilder) {
    final List<Sort> sorts = query.getSort();
    if (sorts != null) {
      searchSourceBuilder.sort(
          sorts.stream()
              .map(
                  sort -> {
                    final Order order = sort.getOrder();
                    final FieldSortBuilder sortBuilder = SortBuilders.fieldSort(sort.getField());
                    if (order.equals(Order.DESC)) {
                      return sortBuilder.order(SortOrder.DESC);
                    } else {
                      // if not specified always assume ASC order
                      return sortBuilder.order(SortOrder.ASC);
                    }
                  })
              .collect(Collectors.toList()));
    }
    // always sort at least by key - needed for searchAfter method of paging
    searchSourceBuilder.sort(SortBuilders.fieldSort(uniqueSortKey).order(SortOrder.ASC));
  }

  protected void buildPaging(final Query<T> query, final SearchSourceBuilder searchSourceBuilder) {
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null) {
      searchSourceBuilder.searchAfter(searchAfter);
    }
    searchSourceBuilder.size(query.getSize());
  }

  protected SearchSourceBuilder buildQueryOn(
      final Query<T> query, final String uniqueKey, final SearchSourceBuilder searchSourceBuilder) {
    logger.debug("Build query for Elasticsearch from query {}", query);
    buildSorting(query, uniqueKey, searchSourceBuilder);
    buildPaging(query, searchSourceBuilder);
    buildFiltering(query, searchSourceBuilder);
    return searchSourceBuilder;
  }

  protected abstract void buildFiltering(
      final Query<T> query, final SearchSourceBuilder searchSourceBuilder);

  protected QueryBuilder buildTermQuery(final String name, final String value) {
    if (!stringIsEmpty(value)) {
      return termQuery(name, value);
    }
    return null;
  }

  protected QueryBuilder buildTermQuery(final String name, final Integer value) {
    if (value != null) {
      return termQuery(name, value);
    }
    return null;
  }

  protected QueryBuilder buildTermQuery(final String name, final Long value) {
    if (value != null) {
      return termQuery(name, value);
    }
    return null;
  }

  protected QueryBuilder buildTermQuery(final String name, final Boolean value) {
    if (value != null) {
      return termQuery(name, value);
    }
    return null;
  }

  protected QueryBuilder buildMatchQuery(final String name, final String value) {
    if (value != null) {
      return matchQuery(name, value).operator(Operator.AND);
    }
    return null;
  }

  protected QueryBuilder buildMatchDateQuery(final String name, final String dateAsString) {
    if (dateAsString != null) {
      // Used to match in different time ranges like hours, minutes etc
      // See:
      // https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#date-math
      return rangeQuery(name)
          .gte(dateAsString)
          .lte(dateAsString)
          .format(dateTimeFormatter.getApiDateTimeFormatString());
    }
    return null;
  }
}
