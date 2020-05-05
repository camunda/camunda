/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createInstance} from 'modules/testUtils';
import {PATHNAME} from './constants';

export const location = {
  dashboard: {
    pathname: PATHNAME.DASHBOARD,
  },
  instances: {
    pathname: PATHNAME.INSTANCES,
  },
  instance: {
    pathname: PATHNAME.INSTANCE,
  },
};

export const mockInstance = createInstance();
