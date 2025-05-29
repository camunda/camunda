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

import io.camunda.client.api.response.UpdateUserResponse;

public interface UpdateUserCommandStep1 extends FinalCommandStep<UpdateUserResponse> {

  /**
   * Set the name for the user to be updated.
   *
   * @param name the name of the user
   * @return the builder for this command
   */
  UpdateUserCommandStep1 name(String name);

  /**
   * Set the email for the user to be updated.
   *
   * @param email the user email
   * @return the builder for this command
   */
  UpdateUserCommandStep1 email(String email);

  /**
   * Set the password for the user to be updated.
   *
   * @param password the user password
   * @return the builder for this command
   */
  UpdateUserCommandStep1 password(String password);
}
