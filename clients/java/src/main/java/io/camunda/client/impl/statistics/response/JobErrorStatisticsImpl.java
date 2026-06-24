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
package io.camunda.client.impl.statistics.response;

import io.camunda.client.api.search.response.SearchResponsePage;
import io.camunda.client.api.statistics.response.JobErrorStatistics;
import io.camunda.client.api.statistics.response.JobErrorStatisticsItem;
import java.util.List;
import java.util.Objects;

public class JobErrorStatisticsImpl implements JobErrorStatistics {

  private final List<JobErrorStatisticsItem> items;
  private final SearchResponsePage page;

  public JobErrorStatisticsImpl(
      final List<JobErrorStatisticsItem> items, final SearchResponsePage page) {
    this.items = items;
    this.page = page;
  }

  @Override
  public List<JobErrorStatisticsItem> items() {
    return items;
  }

  @Override
  public SearchResponsePage page() {
    return page;
  }

  @Override
  public int hashCode() {
    return Objects.hash(items, page);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final JobErrorStatisticsImpl that = (JobErrorStatisticsImpl) obj;
    return Objects.equals(items, that.items) && Objects.equals(page, that.page);
  }

  @Override
  public String toString() {
    return "JobErrorStatisticsImpl{" + "items=" + items + ", page=" + page + '}';
  }
}
