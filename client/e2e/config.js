/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export default {
  endpoint:
    process.env.ENV === 'ci'
      ? 'http://localhost:8080/'
      : 'http://localhost:3000',
  agentUser: {
    username: 'demo',
    password: 'demo'
  }
};
