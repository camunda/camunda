/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
