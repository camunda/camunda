/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';
import type {
  ProcessInstanceEntity,
  VariableEntity,
} from 'modules/types/operate';

const fetchVariable = async (
  processInstanceId: ProcessInstanceEntity['id'],
  variableId: VariableEntity['id'],
) => {
  return requestAndParse<VariableEntity>({
    url: `/api/process-instances/${processInstanceId}/variables/${variableId}`,
  });
};

export {fetchVariable};
