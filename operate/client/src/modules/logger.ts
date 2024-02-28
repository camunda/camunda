/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const logger = {
  error(message: unknown | Error) {
    if (['production', 'development'].includes(process.env.NODE_ENV)) {
      console.error(message);
    }
  },
} as const;

export {logger};
