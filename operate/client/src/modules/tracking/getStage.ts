/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

function getStage(host: string): 'dev' | 'int' | 'prod' | 'unknown' {
  if (host.includes(process.env.REACT_APP_DEV_ENV_URL)) {
    return 'dev';
  }

  if (host.includes(process.env.REACT_APP_INT_ENV_URL)) {
    return 'int';
  }

  if (host.includes(process.env.REACT_APP_PROD_ENV_URL)) {
    return 'prod';
  }

  return 'unknown';
}

export {getStage};
