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
package io.camunda.zeebe.spring.common.auth.saas;

import io.camunda.zeebe.spring.common.auth.jwt.JwtAuthenticationBuilder;
import io.camunda.zeebe.spring.common.auth.jwt.JwtConfig;
import io.camunda.zeebe.spring.common.json.JsonMapper;

public class SaaSAuthenticationBuilder extends JwtAuthenticationBuilder<SaaSAuthenticationBuilder> {
  private JsonMapper jsonMapper;

  public SaaSAuthenticationBuilder withJsonMapper(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    return this;
  }

  @Override
  protected SaaSAuthenticationBuilder self() {
    return this;
  }

  @Override
  protected SaaSAuthentication build(final JwtConfig jwtConfig) {
    return new SaaSAuthentication(jwtConfig, jsonMapper);
  }
}
