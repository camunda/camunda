/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestWithThrow} from 'modules/request';
import {type CurrentUser, endpoints} from '@vzeta/camunda-api-zod-schemas/8.8';

const getMe = async () => {
  return requestWithThrow<CurrentUser>({
    url: endpoints.getCurrentUser.getUrl(),
    method: endpoints.getCurrentUser.method,
  });
};

export {getMe};
