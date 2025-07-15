/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstanceDeprecated} from '../processInstance/deprecated/useProcessInstanceDeprecated';
import {hasActiveOperations} from 'modules/stores/utils/hasActiveOperations';
import type {ProcessInstanceEntity} from 'modules/types/operate';

const hasActiveOperationsParser = (data: ProcessInstanceEntity) => {
  return hasActiveOperations(data.operations);
};

const useHasActiveOperations = () =>
  useProcessInstanceDeprecated<boolean>(hasActiveOperationsParser);

export {useHasActiveOperations};
