/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';

const isInstanceRunning = (instance) => {
  return instance?.state === STATE.ACTIVE || instance?.state === STATE.INCIDENT;
};

export {isInstanceRunning};
