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
package io.camunda.zeebe.spring.common.auth.simple;

import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.Authentication.AuthenticationBuilder;

public class SimpleAuthenticationBuilder implements AuthenticationBuilder {
  private String simpleUrl;
  private SimpleConfig simpleConfig;

  public SimpleAuthenticationBuilder withSimpleUrl(final String simpleUrl) {
    this.simpleUrl = simpleUrl;
    return this;
  }

  public SimpleAuthenticationBuilder withSimpleConfig(final SimpleConfig simpleConfig) {
    this.simpleConfig = simpleConfig;
    return this;
  }

  @Override
  public Authentication build() {
    return new SimpleAuthentication(simpleUrl, simpleConfig);
  }
}
