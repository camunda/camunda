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
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.response.CreateUserResponse;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.CreateUserCommandStep1}
 */
@Deprecated
public interface CreateUserCommandStep1 extends FinalCommandStep<CreateUserResponse> {

  /**
   * Set the username to create user with.
   *
   * @param username the username value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CreateUserCommandStep1 username(String username);

  /**
   * Set the email to create user with.
   *
   * @param email the email value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CreateUserCommandStep1 email(String email);

  /**
   * Set the name to create user with.
   *
   * @param name the name value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CreateUserCommandStep1 name(String name);

  /**
   * Set the password to create user with.
   *
   * @param password the password value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CreateUserCommandStep1 password(String password);
}
