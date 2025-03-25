/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RequestResult, requestWithThrow} from 'modules/request';

type ProcessDefinitionKey = string | undefined;
type ProcessDefinitionXmlDto = string;

const fetchProcessDefinitionXml = async (
  processDefinitionKey: ProcessDefinitionKey,
): RequestResult<ProcessDefinitionXmlDto> => {
  return requestWithThrow({
    url: `/v2/process-definitions/${processDefinitionKey}/xml`,
    method: 'GET',
    responseType: 'text',
  });
};

export {fetchProcessDefinitionXml};
export type {ProcessDefinitionKey, ProcessDefinitionXmlDto};
