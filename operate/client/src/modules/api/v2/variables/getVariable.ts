/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {endpoints, type Variable} from '@vzeta/camunda-api-zod-schemas/8.8';
import {type RequestResult, requestWithThrow} from 'modules/request';

const getVariable = async (variableKey: string): RequestResult<Variable> => {
  return requestWithThrow<Variable>({
    url: endpoints.getVariable.getUrl({variableKey}),
    method: endpoints.getVariable.method,
  });
};

export {getVariable};
