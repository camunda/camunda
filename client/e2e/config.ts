/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const config = {
  endpoint: `http://localhost:${process.env.PORT}`,
  agentUser: {
    username: 'demo',
    password: 'demo',
  },
} as const;

export {config};
