/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RequestResult, requestWithThrow} from 'modules/request';

type DecisionDefinitionKey = string | undefined;
type DecisionDefinitionXmlDto = string;

const fetchDecisionDefinitionXml = async (
  DecisionDefinitionKey: DecisionDefinitionKey,
): RequestResult<DecisionDefinitionXmlDto> => {
  return requestWithThrow({
    url: `/v2/decision-definitions/${DecisionDefinitionKey}/xml`,
    method: 'GET',
    responseType: 'text',
  });
};

export {fetchDecisionDefinitionXml};
export type {DecisionDefinitionKey, DecisionDefinitionXmlDto};
