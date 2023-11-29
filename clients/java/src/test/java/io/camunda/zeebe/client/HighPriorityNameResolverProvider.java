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
package io.camunda.zeebe.client;

import io.camunda.zeebe.client.api.command.ClientException;
import io.grpc.NameResolver;
import io.grpc.NameResolver.Args;
import io.grpc.NameResolverProvider;
import java.net.URI;

/**
 * @author liweiye😜
 * @version 1.0.0
 * @since 2023/10/19
 */
public class HighPriorityNameResolverProvider extends NameResolverProvider {

  @Override
  protected boolean isAvailable() {
    return true;
  }

  @Override
  protected int priority() {
    // set the highest priority
    return 10;
  }

  @Override
  public NameResolver newNameResolver(final URI uri, final Args args) {
    throw new ClientException(
        "can not resolve uri: " + uri.toString() + " with scheme: " + uri.getScheme());
  }

  @Override
  public String getDefaultScheme() {
    return "demo";
  }
}
