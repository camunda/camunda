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
package io.camunda.zeebe.spring.common.auth;

import io.camunda.zeebe.spring.common.exception.SdkException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation for Authentication Typically you will replace this by a proper
 * authentication by setting the right properties
 */
public class DefaultNoopAuthentication implements Authentication {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String errorMessage =
      "Unable to determine authentication. Please check your configuration";

  public DefaultNoopAuthentication() {
    LOG.error(errorMessage);
  }

  @Override
  public Map.Entry<String, String> getTokenHeader(final Product product) {
    throw new UnsupportedOperationException("Unable to determine authentication");
  }

  @Override
  public void resetToken(final Product product) {
    throw new SdkException(errorMessage);
  }
}
