/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {createInstance, createOperation} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstancesStore} from 'modules/stores/processInstances';
import {LocationLog} from 'modules/utils/LocationLog';
import {Paths} from 'modules/Routes';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      modificationsStore.reset();
      processInstancesStore.reset();
    };
  }, []);

  return (
    <MemoryRouter initialEntries={[Paths.processes()]}>
      <Routes>
        <Route path={Paths.processInstance()} element={children} />
        <Route path={Paths.processes()} element={children} />
      </Routes>
      <LocationLog />
    </MemoryRouter>
  );
};

const INSTANCE = createInstance({
  id: 'instance_1',
  operations: [createOperation({state: 'FAILED'})],
  hasActiveOperation: false,
});
const ACTIVE_INSTANCE = createInstance({
  id: 'instance_1',
  operations: [createOperation({state: 'SENT'})],
  hasActiveOperation: true,
});

export {Wrapper, INSTANCE, ACTIVE_INSTANCE};
