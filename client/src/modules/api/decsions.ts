/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {request} from 'modules/request';

async function fetchDecisionXML(decisionDefinitionId: string) {
  return request({
    url: `/api/decisions/${decisionDefinitionId}/xml`,
    method: 'GET',
  });
}

async function fetchDecisionInstance(decisionInstanceId: string) {
  return request({
    url: `/api/decision-instances/${decisionInstanceId}`,
    method: 'GET',
  });
}

export {fetchDecisionXML, fetchDecisionInstance};
