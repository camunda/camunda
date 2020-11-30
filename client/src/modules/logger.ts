/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const logger = {
  error(message: string | Error) {
    if (['production', 'development'].includes(process.env.NODE_ENV)) {
      console.error(message);
    }
  },
} as const;

export {logger};
