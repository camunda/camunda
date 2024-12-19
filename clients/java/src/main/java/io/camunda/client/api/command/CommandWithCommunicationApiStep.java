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
package io.camunda.client.api.command;

import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.ExperimentalApi;

public interface CommandWithCommunicationApiStep<T> {

  /**
   * <strong>Experimental: This method is under development, and as such using it may have no effect
   * on the command builder when called. While unimplemented, it simply returns the command builder
   * instance unchanged. This method already exists for software that is building support for a REST
   * API in Camunda, and already wants to use this API during its development. As support for REST
   * is added to Camunda, each of the commands that implement this method may start to take effect.
   * Until this warning is removed, anything described below may not yet have taken effect, and the
   * interface and its description are subject to change.</strong>
   *
   * <p>Sets REST as the communication API for this command. If this command doesn't support
   * communication over REST, it simply returns the command builder instance unchanged. The default
   * communication API can be configured using {@link
   * CamundaClientBuilder#preferRestOverGrpc(boolean)}.
   *
   * @deprecated since 8.5, to be removed with 8.8
   * @return the configured command
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/16166")
  @Deprecated
  T useRest();

  /**
   * <strong>Experimental: This method is under development, and as such using it may have no effect
   * on the command builder when called. While unimplemented, it simply returns the command builder
   * instance unchanged. This method already exists for software that is building support for a REST
   * API in Camunda, and already wants to use this API during its development. As support for REST
   * is added to Zeebe, each of the commands that implement this method may start to take effect.
   * Until this warning is removed, anything described below may not yet have taken effect, and the
   * interface and its description are subject to change.</strong>
   *
   * <p>Sets gRPC as the communication API for this command. If this command doesn't support
   * communication over gRPC, it simply returns the command builder instance unchanged. The default
   * communication API can be configured using {@link
   * CamundaClientBuilder#preferRestOverGrpc(boolean)}.
   *
   * @deprecated since 8.5, to be removed with 8.8
   * @return the configured command
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/16166")
  @Deprecated
  T useGrpc();
}
