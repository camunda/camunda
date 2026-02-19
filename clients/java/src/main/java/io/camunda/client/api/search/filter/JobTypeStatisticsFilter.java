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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.function.Consumer;

/** Filter for job type statistics queries. */
public interface JobTypeStatisticsFilter extends SearchRequestFilter {

  /**
   * Filter by exact job type match.
   *
   * <pre>
   * filter(f -&gt; f.jobType("myJobType"))
   * </pre>
   *
   * @param jobType the job type to filter by
   * @return the updated filter
   */
  JobTypeStatisticsFilter jobType(String jobType);

  /**
   * Filter by job type using advanced string filtering capabilities.
   *
   * <p>Supports: eq, neq, like, in, notIn, exists
   *
   * <pre>
   * filter(f -&gt; f.jobType(jt -&gt; jt.like("fetch-*")))
   * filter(f -&gt; f.jobType(jt -&gt; jt.in("type1", "type2")))
   * </pre>
   *
   * @param fn the job type {@link StringProperty} consumer
   * @return the updated filter
   */
  JobTypeStatisticsFilter jobType(Consumer<StringProperty> fn);
}
