/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {processesStore} from 'modules/stores/processes/processes.list';
import {useFilters} from 'modules/hooks/useFilters';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {PAGE_TITLE} from 'modules/constants';
import {Filters} from './Filters';
import {InstancesTable} from './InstancesTable';
import {Container} from './styled';

const OperationsLog: React.FC = () => {
  const location = useLocation();
  const {getFilters} = useFilters();
  const filters = getFilters();
  const {process, tenant, version} = filters;

  const processDefinitionKey = processesStore.getProcessId({
    process,
    tenant,
    version,
  });

  useEffect(() => {
    if (
      processesStore.state.status !== 'initial' &&
      location.state?.refreshContent
    ) {
      processesStore.fetchProcesses();
    }
  }, [location.state]);

  useEffect(() => {
    processInstancesSelectionStore.init();
    processesStore.fetchProcesses();

    document.title = PAGE_TITLE.AUDIT_LOG;

    return () => {
      processesStore.reset();
    };
  }, []);

  return (
    <ProcessDefinitionKeyContext.Provider value={processDefinitionKey}>
      <Container>
        <Filters />
        <InstancesTable />
      </Container>
    </ProcessDefinitionKeyContext.Provider>
  );
};

export {OperationsLog};
