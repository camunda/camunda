/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.data;

public interface DataGenerator {

  DataGenerator DO_NOTHING =
      new DataGenerator() {
        @Override
        public void createZeebeDataAsync() {
          /*empty*/
        }

        @Override
        public void createUser(String username, String firstname, String lastname) {
          /*empty*/
        }

        @Override
        public boolean shouldCreateData() {
          return false;
        }

        @Override
        public void createDemoUsers() {
          /*empty*/
        }
      };

  void createZeebeDataAsync();

  public void createUser(String username, String firstname, String lastname);

  public boolean shouldCreateData();

  public void createDemoUsers();
}
