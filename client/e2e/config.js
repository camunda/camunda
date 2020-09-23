/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const config = {
  endpoint: `http://localhost:${process.env.PORT}`,
  agentUser: {
    username: 'demo',
    password: 'demo',
  },
  window: {
    width: 1920,
    height: 950,
  },
};

export {config};
