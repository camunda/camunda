/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {useProcessInstance} from './useProcessInstance';
import {PAGE_TITLE} from 'modules/constants';

const processTitleParser = (processInstance: ProcessInstance): string => {
  const {processDefinitionName, processDefinitionId} = processInstance;

  return PAGE_TITLE.INSTANCE(
    processInstance.processInstanceKey,
    processDefinitionName || processDefinitionId || '',
  );
};

const useProcessTitle = () => useProcessInstance<string>(processTitleParser);

export {useProcessTitle};
