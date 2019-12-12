/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';

export async function isEventBasedProcessEnabled() {
  const response = await get('api/eventBasedProcess/isEnabled');
  const value = await response.text();

  return value === 'true';
}
