/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext} from 'react';

type ProcessInstanceOperationsContextType = {
  cancellation: {
    onMutate: () => void;
    onError: (error: Error) => void;
    isPending: boolean;
  };
};

const ProcessInstanceOperationsContext = createContext<
  ProcessInstanceOperationsContextType | undefined
>(undefined);

const useProcessInstanceOperationsContext = () => {
  return useContext(ProcessInstanceOperationsContext);
};

export {ProcessInstanceOperationsContext, useProcessInstanceOperationsContext};
export type {ProcessInstanceOperationsContextType};
