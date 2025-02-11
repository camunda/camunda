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
package io.camunda.zeebe.client.protocol.rest;

import java.util.Objects;

/**
 * Added to keep compatibility with the previous version of the client. Used in {@link
 * io.camunda.zeebe.client.api.command.UpdateJobCommandStep1#update(JobChangeset)}
 *
 * @deprecated since 8.8 for removal in 8.9, replaced by #TODO
 *     https://github.com/camunda/camunda/issues/26851
 */
@Deprecated
public class JobChangeset {

  private final io.camunda.client.protocol.rest.JobChangeset delegate;

  public JobChangeset() {
    delegate = new io.camunda.client.protocol.rest.JobChangeset();
  }

  public io.camunda.client.protocol.rest.JobChangeset getDelegate() {
    return delegate;
  }

  public JobChangeset retries(final Integer retries) {
    delegate.retries(retries);
    return this;
  }

  public Integer getRetries() {
    return delegate.getRetries();
  }

  public void setRetries(final Integer retries) {
    delegate.setRetries(retries);
  }

  public JobChangeset timeout(final Long timeout) {
    delegate.timeout(timeout);
    return this;
  }

  public Long getTimeout() {
    return delegate.getTimeout();
  }

  public void setTimeout(final Long timeout) {
    delegate.setTimeout(timeout);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(delegate);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JobChangeset that = (JobChangeset) o;
    return Objects.equals(delegate, that.delegate);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  public String toUrlQueryString() {
    return delegate.toUrlQueryString();
  }

  public String toUrlQueryString(final String prefix) {
    return delegate.toUrlQueryString(prefix);
  }
}
