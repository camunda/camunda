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
package io.camunda.tasklist.webapp.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.es.tenant.ElasticsearchTenantCheckApplier;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticSearchTenantCheckApplierTest {

  @Mock private TenantService tenantService;

  @InjectMocks private ElasticsearchTenantCheckApplier instance;

  @Test
  void checkIfQueryContainsTenant() {
    // given
    final SearchRequest searchRequest = new SearchRequest("TaskTest");
    searchRequest.source().query(termsQuery("test", "1"));
    final TenantService.AuthenticatedTenants authenticatedTenants = mock();
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(authenticatedTenants.getTenantIds()).thenReturn(authorizedTenant);
    final String queryResult =
        "{\n"
            + "  \"bool\" : {\n"
            + "    \"must\" : [\n"
            + "      {\n"
            + "        \"terms\" : {\n"
            + "          \"tenantId\" : [\n"
            + "            \"TenantA\",\n"
            + "            \"TenantB\"\n"
            + "          ],\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        \"terms\" : {\n"
            + "          \"test\" : [\n"
            + "            \"1\"\n"
            + "          ],\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      }\n"
            + "    ],\n"
            + "    \"adjust_pure_negative\" : true,\n"
            + "    \"boost\" : 1.0\n"
            + "  }\n"
            + "}";

    when(authenticatedTenants.getTenantAccessType())
        .thenReturn(TenantService.TenantAccessType.TENANT_ACCESS_ASSIGNED);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);

    // when
    instance.apply(searchRequest);

    // then
    assertThat(searchRequest.source().query().toString()).isEqualTo(queryResult);
  }

  @Test
  void checkIfQueryContainsAccessibleTenantsProvidedByUser() {
    // given
    final SearchRequest searchRequest = new SearchRequest("TaskTest");
    searchRequest.source().query(termsQuery("test", "1"));
    final TenantService.AuthenticatedTenants authenticatedTenants = mock();
    final List<String> tenantsProvidedByUser = List.of("TenantA", "TenantC");
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(authenticatedTenants.getTenantIds()).thenReturn(authorizedTenant);
    final String queryResult =
        "{\n"
            + "  \"bool\" : {\n"
            + "    \"must\" : [\n"
            + "      {\n"
            + "        \"terms\" : {\n"
            + "          \"tenantId\" : [\n"
            + "            \"TenantA\"\n"
            + "          ],\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        \"terms\" : {\n"
            + "          \"test\" : [\n"
            + "            \"1\"\n"
            + "          ],\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      }\n"
            + "    ],\n"
            + "    \"adjust_pure_negative\" : true,\n"
            + "    \"boost\" : 1.0\n"
            + "  }\n"
            + "}";

    when(authenticatedTenants.getTenantAccessType())
        .thenReturn(TenantService.TenantAccessType.TENANT_ACCESS_ASSIGNED);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);

    // when
    instance.apply(searchRequest, tenantsProvidedByUser);

    // then
    assertThat(searchRequest.source().query().toString()).isEqualTo(queryResult);
  }

  @Test
  void checkShouldReturnNoneMatchQueryIfUserProvidedNotAccessibleTenants() {
    // given
    final SearchRequest searchRequest = new SearchRequest("TaskTest");
    searchRequest.source().query(termsQuery("test", "1"));
    final TenantService.AuthenticatedTenants authenticatedTenants = mock();
    final List<String> tenantsProvidedByUser = List.of("UnknownTenant");
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(authenticatedTenants.getTenantIds()).thenReturn(authorizedTenant);

    when(authenticatedTenants.getTenantAccessType())
        .thenReturn(TenantService.TenantAccessType.TENANT_ACCESS_ASSIGNED);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);

    // when
    instance.apply(searchRequest, tenantsProvidedByUser);

    // then
    assertThat(searchRequest.source().query().toString())
        .isEqualTo(ElasticsearchUtil.createMatchNoneQuery().toString());
  }

  @Test
  void checkShouldReturnNoneMatchQueryIfUserHaveNoneTenantsAccess() {
    // given
    final SearchRequest searchRequest = new SearchRequest("TaskTest");
    searchRequest.source().query(termsQuery("test", "1"));
    final TenantService.AuthenticatedTenants authenticatedTenants = mock();
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);
    when(authenticatedTenants.getTenantIds()).thenReturn(Collections.emptyList());
    when(authenticatedTenants.getTenantAccessType())
        .thenReturn(TenantService.TenantAccessType.TENANT_ACCESS_NONE);

    // when
    instance.apply(searchRequest);

    // then
    assertThat(searchRequest.source().query().toString())
        .isEqualTo(ElasticsearchUtil.createMatchNoneQuery().toString());
  }

  @Test
  void checkThatQueryDontContainTenantWhenMultiTenancyIsTurnedOff() {
    // given
    final SearchRequest searchRequest = new SearchRequest("TaskTest");
    searchRequest.source().query(termsQuery("test", "1"));
    final TenantService.AuthenticatedTenants authenticatedTenants = mock();
    when(authenticatedTenants.getTenantIds()).thenReturn(Collections.emptyList());
    when(authenticatedTenants.getTenantAccessType())
        .thenReturn(TenantService.TenantAccessType.TENANT_ACCESS_ALL);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);
    final String expectedQueryResult =
        "{\n"
            + "  \"terms\" : {\n"
            + "    \"test\" : [\n"
            + "      \"1\"\n"
            + "    ],\n"
            + "    \"boost\" : 1.0\n"
            + "  }\n"
            + "}";

    // when
    instance.apply(searchRequest);

    // then
    assertThat(searchRequest.source().query().toString()).isEqualTo(expectedQueryResult);
  }
}
