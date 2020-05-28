/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from 'apollo-boost';

const GET_TASKS = gql`
  query GetTasks {
    tasks @client {
      key
      name
      assignee
    }
  }
`;

export {GET_TASKS};
