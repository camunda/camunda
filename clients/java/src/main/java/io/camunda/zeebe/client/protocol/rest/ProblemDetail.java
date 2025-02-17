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

import java.net.URI;
import java.util.Objects;

/**
 * Added to keep compatibility with the previous version of the client. Used in {@link
 * io.camunda.zeebe.client.api.command.ProblemException#details()}.
 *
 * @deprecated since 8.8 for removal in 8.9, replaced by #TODO
 *     https://github.com/camunda/camunda/issues/26851
 */
@Deprecated
public class ProblemDetail {

  private final io.camunda.client.protocol.rest.ProblemDetail delegate;

  public ProblemDetail(final io.camunda.client.protocol.rest.ProblemDetail delegate) {
    this.delegate = delegate;
  }

  public io.camunda.client.protocol.rest.ProblemDetail getDelegate() {
    return delegate;
  }

  public ProblemDetail type(final URI type) {
    delegate.type(type);
    return this;
  }

  public URI getType() {
    return delegate.getType();
  }

  public void setType(final URI type) {
    delegate.setType(type);
  }

  public ProblemDetail title(final String title) {
    delegate.title(title);
    return this;
  }

  public String getTitle() {
    return delegate.getTitle();
  }

  public void setTitle(final String title) {
    delegate.setTitle(title);
  }

  public ProblemDetail status(final Integer status) {
    delegate.status(status);
    return this;
  }

  public Integer getStatus() {
    return delegate.getStatus();
  }

  public void setStatus(final Integer status) {
    delegate.setStatus(status);
  }

  public ProblemDetail detail(final String detail) {
    delegate.detail(detail);
    return this;
  }

  public String getDetail() {
    return delegate.getDetail();
  }

  public void setDetail(final String detail) {
    delegate.setDetail(detail);
  }

  public ProblemDetail instance(final URI instance) {
    delegate.instance(instance);
    return this;
  }

  public URI getInstance() {
    return delegate.getInstance();
  }

  public void setInstance(final URI instance) {
    delegate.setInstance(instance);
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
    final ProblemDetail that = (ProblemDetail) o;
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
