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
package io.camunda.client.api.response;

public interface UpdateUserResponse {

  /**
   * Returns the key of the updated user.
   *
   * @return the key of the updated user.
   */
  String getUserKey();

  /**
   * Returns the username of the updated user.
   *
   * @return the username of the updated user.
   */
  String getUsername();

  /**
   * Returns the name of the updated user.
   *
   * @return the name of the updated user.
   */
  String getName();

  /**
   * Returns the email of the updated user.
   *
   * @return the email of the updated user.
   */
  String getEmail();
}
