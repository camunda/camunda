/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.request.ClientsByGroupSearchRequest;
import io.camunda.client.api.search.request.ClientsByRoleSearchRequest;
import io.camunda.client.api.search.request.ClientsByTenantSearchRequest;
import io.camunda.client.api.search.request.GroupsByRoleSearchRequest;
import io.camunda.client.api.search.request.GroupsByTenantSearchRequest;
import io.camunda.client.api.search.request.MappingRulesByGroupSearchRequest;
import io.camunda.client.api.search.request.MappingRulesByRoleSearchRequest;
import io.camunda.client.api.search.request.MappingRulesByTenantSearchRequest;
import io.camunda.client.api.search.request.RolesByGroupSearchRequest;
import io.camunda.client.api.search.request.RolesByTenantSearchRequest;
import io.camunda.client.api.search.request.TypedFilterableRequest;
import io.camunda.client.api.search.request.TypedPageableRequest;
import io.camunda.client.api.search.request.TypedSortableRequest;
import io.camunda.client.api.search.request.UsersByGroupSearchRequest;
import io.camunda.client.api.search.request.UsersByRoleSearchRequest;
import io.camunda.client.api.search.request.UsersByTenantSearchRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that membership / nested sub-collection search requests, whose REST contract does not
 * expose a {@code filter} property, do not offer filtering in the Java client either, while
 * top-level collection requests reused by {@code …ByX} endpoints keep it.
 *
 * <p>These endpoints are scoped by a path parameter (the group / role / tenant id), so a membership
 * listing has nothing left to filter on — it only offers sort + pagination. Removing {@code
 * filter(...)} from these interfaces makes calling it a compile-time error instead of a runtime
 * {@link UnsupportedOperationException}.
 */
public class FilterTypeSafetyTest {

  /**
   * Membership sub-collection requests that must NOT expose filtering (no REST filter property).
   */
  private static final List<Class<?>> NON_FILTERABLE_REQUESTS =
      Arrays.asList(
          UsersByGroupSearchRequest.class,
          ClientsByGroupSearchRequest.class,
          UsersByRoleSearchRequest.class,
          ClientsByRoleSearchRequest.class,
          GroupsByRoleSearchRequest.class,
          UsersByTenantSearchRequest.class,
          ClientsByTenantSearchRequest.class,
          GroupsByTenantSearchRequest.class);

  /**
   * {@code …ByX} requests that reuse a filterable top-level REST schema and must keep filtering.
   */
  private static final List<Class<?>> FILTERABLE_REQUESTS =
      Arrays.asList(
          MappingRulesByGroupSearchRequest.class,
          MappingRulesByRoleSearchRequest.class,
          MappingRulesByTenantSearchRequest.class,
          RolesByGroupSearchRequest.class,
          RolesByTenantSearchRequest.class);

  @Test
  void nonFilterableRequestsShouldNotExtendTypedFilterableRequest() {
    for (final Class<?> request : NON_FILTERABLE_REQUESTS) {
      assertThat(TypedFilterableRequest.class.isAssignableFrom(request))
          .as("%s must not extend TypedFilterableRequest", request.getSimpleName())
          .isFalse();
    }
  }

  @Test
  void nonFilterableRequestsShouldNotExposeAFilterMethod() {
    for (final Class<?> request : NON_FILTERABLE_REQUESTS) {
      assertThat(hasFilterMethod(request))
          .as("%s must not expose a filter(...) method", request.getSimpleName())
          .isFalse();
    }
  }

  @Test
  void nonFilterableRequestsShouldStillSupportSortingAndPagination() {
    for (final Class<?> request : NON_FILTERABLE_REQUESTS) {
      assertThat(TypedSortableRequest.class.isAssignableFrom(request))
          .as("%s must still support sorting", request.getSimpleName())
          .isTrue();
      assertThat(TypedPageableRequest.class.isAssignableFrom(request))
          .as("%s must still support pagination", request.getSimpleName())
          .isTrue();
    }
  }

  @Test
  void filterableRequestsShouldStillExposeFiltering() {
    for (final Class<?> request : FILTERABLE_REQUESTS) {
      assertThat(TypedFilterableRequest.class.isAssignableFrom(request))
          .as("%s must keep extending TypedFilterableRequest", request.getSimpleName())
          .isTrue();
      assertThat(hasFilterMethod(request))
          .as("%s must keep a filter(...) method", request.getSimpleName())
          .isTrue();
    }
  }

  private static boolean hasFilterMethod(final Class<?> request) {
    return Arrays.stream(request.getMethods())
        .map(Method::getName)
        .anyMatch(name -> name.equals("filter"));
  }
}
