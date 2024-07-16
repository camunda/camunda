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
package io.camunda.client;

public interface CamundaClientCloudBuilderStep1 {

  /**
   * Sets the cluster id of the Camunda Cloud cluster. This parameter is mandatory.
   *
   * @param clusterId cluster id of the Camunda Cloud cluster.
   */
  CamundaClientCloudBuilderStep2 withClusterId(String clusterId);

  interface CamundaClientCloudBuilderStep2 {

    /**
     * Sets the client id that will be used to authenticate against the Camunda Cloud cluster. This
     * parameter is mandatory.
     *
     * @param clientId client id that will be used in the authentication.
     */
    CamundaClientCloudBuilderStep3 withClientId(String clientId);

    interface CamundaClientCloudBuilderStep3 {

      /**
       * Sets the client secret that will be used to authenticate against the Camunda Cloud cluster.
       * This parameter is mandatory.
       *
       * @param clientSecret client secret that will be used in the authentication.
       */
      CamundaClientCloudBuilderStep4 withClientSecret(String clientSecret);

      interface CamundaClientCloudBuilderStep4 extends CamundaClientBuilder {

        /**
         * Sets the region of the Camunda Cloud cluster. Default is 'bru-2'.
         *
         * @param region region of the Camunda Cloud cluster
         */
        CamundaClientCloudBuilderStep4 withRegion(String region);
      }
    }
  }
}
