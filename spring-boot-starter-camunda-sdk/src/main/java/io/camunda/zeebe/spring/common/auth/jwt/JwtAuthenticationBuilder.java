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
package io.camunda.zeebe.spring.common.auth.jwt;

import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.Authentication.AuthenticationBuilder;

public abstract class JwtAuthenticationBuilder<T extends JwtAuthenticationBuilder<?>>
    implements AuthenticationBuilder {
  private JwtConfig jwtConfig;

  public final T withJwtConfig(final JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
    return self();
  }

  @Override
  public final Authentication build() {
    return build(jwtConfig);
  }

  protected abstract T self();

  protected abstract Authentication build(JwtConfig jwtConfig);
}
