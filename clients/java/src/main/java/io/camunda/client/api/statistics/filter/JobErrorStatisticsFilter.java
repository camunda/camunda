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
package io.camunda.client.api.statistics.filter;

import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.statistics.request.StatisticsRequest.StatisticsRequestFilter;
import java.util.function.Consumer;

/** Filter for job error statistics queries. */
public interface JobErrorStatisticsFilter extends StatisticsRequestFilter {

  /**
   * Filter by exact error code match.
   *
   * <pre>
   * filter(f -&gt; f.errorCode("IO_ERROR"))
   * </pre>
   *
   * @param errorCode the error code to filter by
   * @return the updated filter
   */
  JobErrorStatisticsFilter errorCode(String errorCode);

  /**
   * Filter by error code using advanced string filtering capabilities.
   *
   * <p>Supports: eq, neq, like, in, notIn, exists
   *
   * <pre>
   * filter(f -&gt; f.errorCode(c -&gt; c.like("UNHANDLED_*")))
   * filter(f -&gt; f.errorCode(c -&gt; c.in("IO_ERROR", "TIMEOUT")))
   * </pre>
   *
   * @param fn the error code {@link StringProperty} consumer
   * @return the updated filter
   */
  JobErrorStatisticsFilter errorCode(Consumer<StringProperty> fn);

  /**
   * Filter by exact error message match.
   *
   * <pre>
   * filter(f -&gt; f.errorMessage("Disk full"))
   * </pre>
   *
   * @param errorMessage the error message to filter by
   * @return the updated filter
   */
  JobErrorStatisticsFilter errorMessage(String errorMessage);

  /**
   * Filter by error message using advanced string filtering capabilities.
   *
   * <p>Supports: eq, neq, like, in, notIn, exists
   *
   * <pre>
   * filter(f -&gt; f.errorMessage(m -&gt; m.like("unexpected*")))
   * </pre>
   *
   * @param fn the error message {@link StringProperty} consumer
   * @return the updated filter
   */
  JobErrorStatisticsFilter errorMessage(Consumer<StringProperty> fn);
}
