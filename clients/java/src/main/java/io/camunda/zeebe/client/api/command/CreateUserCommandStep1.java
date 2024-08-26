/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.response.CreateUserResponse;

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
