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
package io.camunda.operate.webapp.opensearch;

import io.camunda.operate.store.opensearch.dsl.QueryDSL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.springframework.stereotype.Component;

/**
 * Wrapper class around the static QueryDSL interface. Enhances testability by allowing classes to
 * utilize the QueryDSL class without static calls, enabling unit tests to mock this out and reduce
 * test complexity
 */
@Component
public class OpensearchQueryDSLWrapper {

  public Query and(Query... queries) {
    return QueryDSL.and(queries);
  }

  public Query and(List<Query> queries) {
    return QueryDSL.and(queries);
  }

  public Query withTenantCheck(Query query) {
    return QueryDSL.withTenantCheck(query);
  }

  public Query constantScore(Query query) {
    return QueryDSL.constantScore(query);
  }

  public Query exists(String field) {
    return QueryDSL.exists(field);
  }

  public <A> Query gt(String field, A gt) {
    return QueryDSL.gt(field, gt);
  }

  public <A> Query gteLte(String field, A gte, A lte) {
    return QueryDSL.gteLte(field, gte, lte);
  }

  public <A> Query gtLte(String field, A gt, A lte) {
    return QueryDSL.gtLte(field, gt, lte);
  }

  public Query hasChildQuery(String type, Query query) {
    return QueryDSL.hasChildQuery(type, query);
  }

  public Query ids(List<String> ids) {
    return QueryDSL.ids(ids);
  }

  public Query ids(Collection<String> ids) {
    return QueryDSL.ids(ids);
  }

  public Query ids(String... ids) {
    return QueryDSL.ids(ids);
  }

  public <C extends Collection<Integer>> Query intTerms(String field, C values) {
    return QueryDSL.intTerms(field, values);
  }

  public <A> JsonData json(A value) {
    return QueryDSL.json(value);
  }

  public <C extends Collection<Long>> Query longTerms(String field, C values) {
    return QueryDSL.longTerms(field, values);
  }

  public <A> Query terms(String field, Collection<A> values, Function<A, FieldValue> toFieldValue) {
    return QueryDSL.terms(field, values, toFieldValue);
  }

  public <A> Query lte(String field, A lte) {
    return QueryDSL.lte(field, lte);
  }

  public <A> Query match(
      String field, A value, Operator operator, Function<A, FieldValue> toFieldValue) {
    return QueryDSL.match(field, value, operator, toFieldValue);
  }

  public Query match(String field, String value, Operator operator) {
    return StringUtils.isBlank(value) ? null : QueryDSL.match(field, value, operator);
  }

  public Query match(String field, String value) {
    return match(field, value, Operator.And);
  }

  public Query matchAll() {
    return QueryDSL.matchAll();
  }

  public Query matchNone() {
    return QueryDSL.matchNone();
  }

  public Query not(Query... queries) {
    return QueryDSL.not(queries);
  }

  public Query or(Query... queries) {
    return QueryDSL.or(queries);
  }

  public Query prefix(String field, String value) {
    return QueryDSL.prefix(field, value);
  }

  public SortOrder reverseOrder(final SortOrder sortOrder) {
    return QueryDSL.reverseOrder(sortOrder);
  }

  public Script script(String script, Map<String, Object> params) {
    return QueryDSL.script(script, params);
  }

  public SortOptions sortOptions(String field, SortOrder sortOrder) {
    return QueryDSL.sortOptions(field, sortOrder);
  }

  public SortOptions sortOptions(String field, SortOrder sortOrder, String missing) {
    return QueryDSL.sortOptions(field, sortOrder, missing);
  }

  public SourceConfig sourceInclude(String... fields) {
    return QueryDSL.sourceInclude(fields);
  }

  public SourceConfig sourceExclude(String... fields) {
    return QueryDSL.sourceExclude(fields);
  }

  public SourceConfig sourceIncludesExcludes(String[] includes, String[] excludes) {
    return QueryDSL.sourceIncludesExcludes(includes, excludes);
  }

  public SourceConfig sourceExclude(List<String> fields) {
    return QueryDSL.sourceExclude(fields);
  }

  public SourceConfig sourceInclude(List<String> fields) {
    return QueryDSL.sourceInclude(fields);
  }

  public SourceConfig sourceIncludesExcludes(List<String> includes, List<String> excludes) {
    return QueryDSL.sourceIncludesExcludes(includes, excludes);
  }

  public <C extends Collection<String>> Query stringTerms(String field, C values) {
    return QueryDSL.stringTerms(field, values);
  }

  public Query term(String field, Integer value) {
    return value == null ? null : QueryDSL.term(field, value);
  }

  public Query term(String field, Long value) {
    return value == null ? null : QueryDSL.term(field, value);
  }

  public Query term(String field, String value) {
    return StringUtils.isBlank(value) ? null : QueryDSL.term(field, value);
  }

  public Query term(String field, boolean value) {
    return QueryDSL.term(field, value);
  }

  public Query term(String field, Boolean value) {
    return value == null ? null : QueryDSL.term(field, value);
  }

  public <A> Query term(String field, A value, Function<A, FieldValue> toFieldValue) {
    return QueryDSL.term(field, value, toFieldValue);
  }

  public Query wildcardQuery(String field, String value) {
    return QueryDSL.wildcardQuery(field, value);
  }

  public Query matchDateQuery(final String name, final String dateAsString, String dateFormat) {
    return StringUtils.isBlank(dateAsString)
        ? null
        : QueryDSL.matchDateQuery(name, dateAsString, dateFormat);
  }
}
