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
package io.camunda.zeebe.client.api.search.query;

import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import java.time.Duration;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link FinalSearchRequestStep}
 */
@Deprecated
public interface FinalSearchQueryStep<T> extends FinalCommandStep<SearchQueryResponse<T>> {

  @Override
  FinalSearchQueryStep<T> requestTimeout(Duration requestTimeout);
}
