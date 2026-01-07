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
import {PAGE_TITLE} from 'modules/constants';
import {Filters} from './Filters';
import {InstancesTable} from './InstancesTable';
import {Container} from './styled';
import {processInstancesStore} from 'modules/stores/processInstances';
import {getProcessInstanceFilters} from 'modules/utils/filter';
import {observer} from 'mobx-react';

const OperationsLog: React.FC = observer(() => {
  const location = useLocation();

  useEffect(() => {
    if (
      processesStore.state.status !== 'initial' &&
      location.state?.refreshContent
    ) {
      processesStore.fetchProcesses();
    }
  }, [location.state]);

  useEffect(() => {
    processesStore.fetchProcesses();

    document.title = PAGE_TITLE.AUDIT_LOG;

    return () => {
      processInstancesStore.reset();
      processesStore.reset();
    };
  }, []);

  const {process, tenant, version} = getProcessInstanceFilters(location.search);
  const processDefinitionKey = processesStore.getProcessId({
    process,
    tenant,
    version,
  });

  return (
    <ProcessDefinitionKeyContext.Provider value={processDefinitionKey}>
      <Container>
        <Filters />
        <InstancesTable />
      </Container>
    </ProcessDefinitionKeyContext.Provider>
  );
});

export {OperationsLog};
