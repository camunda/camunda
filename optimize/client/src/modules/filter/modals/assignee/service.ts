/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {User} from 'components';
import {post, get} from 'request';
import {Definition} from 'types';

export async function loadUsersByDefinition(
  type: string,
  payload: {
    processDefinitionKey: Definition['key'];
    tenantIds: Definition['tenantIds'];
    terms: string;
  }
) {
  const response = await post(`api/${type}/search`, payload);

  return await response.json();
}

export async function loadUsersByReportIds(
  type: string,
  payload: {
    reportIds: string[];
    terms: string;
  }
) {
  const response = await post(`api/${type}/search/reports`, payload);

  return await response.json();
}

export async function getUsersById(type: string, ids: (string | null)[]): Promise<User[]> {
  const response = await get(`api/${type}`, {idIn: ids.join(',')});

  return await response.json();
}
