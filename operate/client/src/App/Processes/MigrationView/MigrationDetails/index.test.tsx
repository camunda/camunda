/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {useEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {MigrationDetails} from '.';
import {createProcessDefinition} from 'modules/testUtils';

const sourceDefinition = createProcessDefinition({
  name: 'New demo process',
  processDefinitionId: 'demoProcess',
  processDefinitionKey: 'demoProcess3',
  version: 3,
  tenantId: '<default>',
});
const targetDefinition = createProcessDefinition({
  name: 'Big variable process',
  processDefinitionId: 'bigVarProcess',
  version: 1,
  tenantId: '<default>',
});

function createWrapper() {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      processInstanceMigrationStore.enable();
      return () => {
        processInstanceMigrationStore.reset();
      };
    }, []);
    return <MemoryRouter>{children}</MemoryRouter>;
  };
  return Wrapper;
}

describe('MigrationDetails', () => {
  it('should render migration details', async () => {
    const queryString =
      '?active=true&incidents=true&processDefinitionId=demoProcess&processDefinitionVersion=3';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    processInstanceMigrationStore.setSelectedInstancesCount(7);
    processInstanceMigrationStore.setCurrentStep('summary');
    processInstanceMigrationStore.setSourceProcessDefinition(sourceDefinition);
    processInstanceMigrationStore.setTargetProcessDefinition(targetDefinition);

    render(<MigrationDetails />, {wrapper: createWrapper()});

    expect(
      screen.getByText(
        /You are about to migrate 7 process instances from the process definition:/i,
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText(/New demo process - version 3/i),
    ).toBeInTheDocument();

    expect(screen.getByText(/to the process definition:/i)).toBeInTheDocument();

    expect(
      screen.getByText(/Big variable process - version 1/i),
    ).toBeInTheDocument();
  });
});
