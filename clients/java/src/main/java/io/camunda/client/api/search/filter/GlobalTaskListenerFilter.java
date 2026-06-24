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

import io.camunda.client.api.search.enums.GlobalListenerSource;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.client.api.search.filter.builder.GlobalListenerSourceProperty;
import io.camunda.client.api.search.filter.builder.GlobalTaskListenerEventTypeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.List;
import java.util.function.Consumer;

public interface GlobalTaskListenerFilter extends SearchRequestFilter {

  /**
   * Filter global listeners by the ID.
   *
   * @param id the ID of the global listener
   * @return the updated filter
   */
  GlobalTaskListenerFilter id(String id);

  /**
   * Filter global listeners by the ID using {@link StringProperty} consumer.
   *
   * @param fn the ID filter consumer
   * @return the updated filter
   */
  GlobalTaskListenerFilter id(Consumer<StringProperty> fn);

  /**
   * Filter global listeners by the job type.
   *
   * @param type the name of the job type, used as a reference to specify which job workers request
   *     the respective listener job
   * @return the updated filter
   */
  GlobalTaskListenerFilter type(String type);

  /**
   * Filter global listeners by the job type using {@link StringProperty} consumer.
   *
   * @param fn the type filter consumer
   * @return the updated filter
   */
  GlobalTaskListenerFilter type(Consumer<StringProperty> fn);

  /**
   * Filter global listeners by a list of supported user task event types.
   *
   * @param eventTypes list of user task event types that trigger the listener
   * @return the updated filter
   */
  GlobalTaskListenerFilter eventTypes(List<GlobalTaskListenerEventType> eventTypes);

  /**
   * Filter global listeners by a list of supported user task event types.
   *
   * @param eventTypes list of user task event types that trigger the listener
   * @return the updated filter
   */
  GlobalTaskListenerFilter eventTypes(GlobalTaskListenerEventType... eventTypes);

  /**
   * Filter global listeners by supported user task event types using {@link
   * GlobalTaskListenerEventTypeProperty} consumer
   *
   * @param fn the event types filter consumer
   * @return the updated filter
   */
  GlobalTaskListenerFilter eventTypes(Consumer<GlobalTaskListenerEventTypeProperty> fn);

  /**
   * Filter global listeners by a supported user task event type.
   *
   * @param eventType user task event types that should trigger the listener
   * @return the updated filter
   */
  GlobalTaskListenerFilter eventType(GlobalTaskListenerEventType eventType);

  /**
   * Filter global listeners by the number of retries.
   *
   * @param retries maximum number of retries attempted by the listener job in case of failure
   * @return the updated filter
   */
  GlobalTaskListenerFilter retries(Integer retries);

  /**
   * Filter global listeners by the number of retries using {@link IntegerProperty} consumer.
   *
   * @param fn the retries filter consumer
   * @return the updated filter
   */
  GlobalTaskListenerFilter retries(Consumer<IntegerProperty> fn);

  /**
   * Filter global listeners by whether the global listener should be executed before or after the
   * model-level ones.
   *
   * @param afterNonGlobal if true, match global listeners executed after the model-level ones. If
   *     false, the ones executed before
   * @return the updated filter
   */
  GlobalTaskListenerFilter afterNonGlobal(Boolean afterNonGlobal);

  /**
   * Filter global listeners that are executed before the model-level ones.
   *
   * @return the updated filter
   */
  GlobalTaskListenerFilter beforeNonGlobal();

  /**
   * Filter global listeners that are executed after the model-level ones.
   *
   * @return the updated filter
   */
  GlobalTaskListenerFilter afterNonGlobal();

  /**
   * Filter global listeners by the priority.
   *
   * @param priority the priority of the global listener
   * @return the updated filter
   */
  GlobalTaskListenerFilter priority(Integer priority);

  /**
   * Filter global listeners by the priority using {@link IntegerProperty} consumer.
   *
   * @param fn the priority filter consumer
   * @return the updated filter
   */
  GlobalTaskListenerFilter priority(Consumer<IntegerProperty> fn);

  /**
   * Filter global listeners by the source.
   *
   * @param source how the global listener was configured (i.e. in the configuration file or via
   *     API)
   * @return the updated filter
   */
  GlobalTaskListenerFilter source(GlobalListenerSource source);

  /**
   * Filter global listeners by the source using {@link GlobalListenerSourceProperty} consumer.
   *
   * @param fn the source filter consumer
   * @return the updated filter
   */
  GlobalTaskListenerFilter source(Consumer<GlobalListenerSourceProperty> fn);
}
