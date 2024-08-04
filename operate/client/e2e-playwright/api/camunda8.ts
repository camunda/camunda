/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Camunda8} from '@camunda8/sdk';

const camunda8 = new Camunda8({
  CAMUNDA_OAUTH_DISABLED: true,
  CAMUNDA_SECURE_CONNECTION: false,
  ZEEBE_ADDRESS: process.env.ZEEBE_GATEWAY_ADDRESS || 'localhost:26500',
});

export {camunda8};
