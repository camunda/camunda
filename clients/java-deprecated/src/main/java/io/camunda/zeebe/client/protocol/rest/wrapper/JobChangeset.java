/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.protocol.rest.wrapper;

import java.util.Objects;

/**
 * Added to keep compatibility with the previous version of the client. Used in {@link
 * io.camunda.zeebe.client.api.command.UpdateJobCommandStep1#update(JobChangeset)}
 *
 * @deprecated since 8.8 for removal in 8.10, replaced by #TODO
 *     https://github.com/camunda/camunda/issues/26851. Please see the <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>.
 */
@Deprecated
public class JobChangeset {

  private final io.camunda.zeebe.client.protocol.rest.JobChangeset delegate;

  public JobChangeset() {
    delegate = new io.camunda.zeebe.client.protocol.rest.JobChangeset();
  }

  public io.camunda.zeebe.client.protocol.rest.JobChangeset getDelegate() {
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
