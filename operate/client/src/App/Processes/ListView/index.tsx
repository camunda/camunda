/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InstancesList} from '../../Layout/InstancesList';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {Filters} from './Filters';
import {InstancesTable} from './InstancesTable/v2';
import {DiagramPanel} from './DiagramPanel/v2';
import {observer} from 'mobx-react';
import {useEffect} from 'react';
import {processesStore} from 'modules/stores/processes/processes.list';
import {deleteSearchParams} from 'modules/utils/filter';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {useLocation, useNavigate, type Location} from 'react-router-dom';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {PAGE_TITLE} from 'modules/constants';
import {notificationsStore} from 'modules/stores/notifications';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {reaction} from 'mobx';
import {tracking} from 'modules/tracking';
import {OperationsPanel} from 'modules/components/OperationsPanel';
import {batchModificationStore} from 'modules/stores/batchModification';
import {ProcessDefinitionKeyContext} from './processDefinitionKeyContext';

type LocationType = Omit<Location, 'state'> & {
  state: {refreshContent?: boolean};
};

const ListView: React.FC = observer(() => {
  const location = useLocation() as LocationType;
  const navigate = useNavigate();

  const filters = getProcessInstanceFilters(location.search);
  const {process, tenant, version} = filters;
  const {
    state: {status: processesStatus},
    isInitialLoadComplete,
  } = processesStore;
  const filtersJSON = JSON.stringify(filters);

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
    processInstancesStore.init();
    processesStore.fetchProcesses();

    document.title = PAGE_TITLE.INSTANCES;

    return () => {
      processInstancesStore.reset();
      processesStore.reset();
    };
  }, []);

  useEffect(() => {
    processInstancesSelectionStore.resetState();
  }, [filtersJSON]);

  useEffect(() => {
    if (isInitialLoadComplete && !location.state?.refreshContent) {
      processInstancesStore.fetchProcessInstancesFromFilters();
    }
  }, [location.search, isInitialLoadComplete, location.state]);

  useEffect(() => {
    if (isInitialLoadComplete && location.state?.refreshContent) {
      processInstancesStore.fetchProcessInstancesFromFilters();
    }
  }, [isInitialLoadComplete, location.state]);

  useEffect(() => {
    const disposer = reaction(
      () => variableFilterStore.state.variable,
      () => {
        if (processesStatus === 'fetched') {
          tracking.track({
            eventName: 'process-instances-filtered',
            filterName: 'variable',
            multipleValues: variableFilterStore.state.isInMultipleMode,
          });
          processInstancesStore.fetchProcessInstancesFromFilters();
        }
      },
    );

    return disposer;
  }, [processesStatus]);

  useEffect(() => {
    if (processesStatus === 'fetched') {
      if (
        process !== undefined &&
        processesStore.getProcess({
          bpmnProcessId: process,
          tenantId: tenant,
        }) === undefined
      ) {
        navigate(deleteSearchParams(location, ['process', 'version']));
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Process could not be found',
          isDismissable: true,
        });
      }
    }
  }, [process, tenant, navigate, processesStatus, location]);

  const processDefinitionKey = processesStore.getProcessId({
    process,
    tenant,
    version,
  });

  return (
    <ProcessDefinitionKeyContext.Provider value={processDefinitionKey}>
      <VisuallyHiddenH1>Operate Process Instances</VisuallyHiddenH1>
      <InstancesList
        type="process"
        leftPanel={<Filters />}
        topPanel={<DiagramPanel />}
        bottomPanel={<InstancesTable />}
        rightPanel={<OperationsPanel />}
        frame={{
          isVisible: batchModificationStore.state.isEnabled,
          headerTitle: 'Batch Modification Mode',
        }}
      />
    </ProcessDefinitionKeyContext.Provider>
  );
});

export {ListView};
