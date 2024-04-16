/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.tasklist.webapp.es.tenant;

import static io.camunda.tasklist.schema.indices.IndexDescriptor.TENANT_ID;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.tenant.TenantCheckApplier;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpenSearchCondition.class)
@Component
public class OpenSearchTenantCheckApplier implements TenantCheckApplier<SearchRequest.Builder> {

  @Autowired private TenantService tenantService;

  @Override
  public void apply(final SearchRequest.Builder searchRequest) {
    final var tenants = tenantService.getAuthenticatedTenants();
    final var tenantCheckQueryType = tenants.getTenantAccessType();
    final var searchByTenantIds = tenants.getTenantIds();

    applyTenantCheckOnQuery(searchRequest, tenantCheckQueryType, searchByTenantIds);
  }

  @Override
  public void apply(SearchRequest.Builder searchRequest, Collection<String> tenantIds) {
    final var tenants = tenantService.getAuthenticatedTenants();
    final var tenantCheckQueryType = tenants.getTenantAccessType();
    final var authorizedTenantIds = Set.copyOf(tenants.getTenantIds());
    final var searchByTenantIds =
        tenantIds.stream().filter(authorizedTenantIds::contains).collect(Collectors.toSet());

    applyTenantCheckOnQuery(searchRequest, tenantCheckQueryType, searchByTenantIds);
  }

  private void applyTenantCheckOnQuery(
      SearchRequest.Builder searchRequest,
      TenantService.TenantAccessType tenantCheckQueryType,
      Collection<String> searchByTenantIds) {
    final var actualQuery = getQueryFromSearchRequestBuilder(searchRequest);

    switch (tenantCheckQueryType) {
      case TENANT_ACCESS_ASSIGNED -> {
        final Query finalQuery;
        if (CollectionUtils.isEmpty(searchByTenantIds)) {
          // no data must be returned
          finalQuery = OpenSearchUtil.createMatchNoneQuery();
        } else {
          final var tenantTermsQuery =
              new Query.Builder()
                  .terms(
                      terms ->
                          terms
                              .field(TENANT_ID)
                              .terms(
                                  values ->
                                      values.value(
                                          searchByTenantIds.stream()
                                              .map(FieldValue::of)
                                              .collect(Collectors.toList()))));
          finalQuery = OpenSearchUtil.joinWithAnd(tenantTermsQuery.build(), actualQuery);
        }
        searchRequest.query(finalQuery);
      }
      case TENANT_ACCESS_NONE -> // no data must be returned
          searchRequest.query(OpenSearchUtil.createMatchNoneQuery());
      case TENANT_ACCESS_ALL -> // return without changing anything in the query
          searchRequest.query(actualQuery);
      default -> {
        final var message =
            String.format("Unexpected tenant check query type %s", tenantCheckQueryType);
        throw new TasklistRuntimeException(message);
      }
    }
  }

  private Query getQueryFromSearchRequestBuilder(final SearchRequest.Builder searchRequest) {
    try {
      final Field privateField = SearchRequest.Builder.class.getDeclaredField("query");
      privateField.setAccessible(true);

      // Store the value of private field in variable
      return (Query) privateField.get(searchRequest);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
