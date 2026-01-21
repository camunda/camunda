/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    return () => {
      processInstanceMigrationStore.reset();
    };
  });

  return (
    <MemoryRouter>
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
        <button
          onClick={() =>
            processInstanceMigrationStore.setCurrentStep('summary')
          }
        >
          Summary
        </button>
        <button
          onClick={() =>
            processInstanceMigrationStore.setCurrentStep('elementMapping')
          }
        >
          Element Mapping
        </button>
        <button
          onClick={() => {
            processInstanceMigrationStore.updateElementMapping({
              sourceId: 'ServiceTask_0kt6c5i',
              targetId: 'ServiceTask_0kt6c5i',
            });
          }}
        >
          map elements
        </button>
      </QueryClientProvider>
    </MemoryRouter>
  );
};

export {Wrapper};
