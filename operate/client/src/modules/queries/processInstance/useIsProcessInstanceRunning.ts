/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstance} from '@vzeta/camunda-api-zod-schemas/operate';
import {useProcessInstance} from './useProcessInstance';

const isRunningParser = (processInstance: ProcessInstance): boolean => {
  return (
    ['ACTIVE'].includes(processInstance.state) || processInstance.hasIncident
  );
};

const useIsProcessInstanceRunning = () =>
  useProcessInstance<boolean>(isRunningParser);

export {useIsProcessInstanceRunning};
