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
package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.filter.AuthorizationFilter;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.OwnerTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import java.util.Arrays;
import java.util.List;

public class AuthorizationFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.AuthorizationFilter>
    implements AuthorizationFilter {

  private final io.camunda.client.protocol.rest.AuthorizationFilter filter;

  public AuthorizationFilterImpl() {
    filter = new io.camunda.client.protocol.rest.AuthorizationFilter();
  }

  @Override
  public AuthorizationFilter ownerId(final String ownerId) {
    filter.setOwnerId(ownerId);
    return this;
  }

  @Override
  public AuthorizationFilter ownerType(final OwnerType ownerType) {
    filter.setOwnerType(EnumUtil.convert(ownerType, OwnerTypeEnum.class));
    return this;
  }

  @Override
  public AuthorizationFilter resourceIds(final String... resourceIds) {
    filter.setResourceIds(Arrays.asList(resourceIds));
    return this;
  }

  @Override
  public AuthorizationFilter resourceIds(final List<String> resourceIds) {
    filter.setResourceIds(resourceIds);
    return this;
  }

  @Override
  public AuthorizationFilter resourceType(final ResourceType resourceType) {
    filter.setResourceType(EnumUtil.convert(resourceType, ResourceTypeEnum.class));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.AuthorizationFilter getSearchRequestProperty() {
    return filter;
  }
}
