/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InstancesList} from '../../../Layout/InstancesList';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {Filters} from './Filters';
import {InstancesTableWrapper} from './InstancesTable/InstancesTableWrapper';
import {DiagramPanel} from './DiagramPanel';
import {observer} from 'mobx-react';
import {useEffect} from 'react';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelectionV2';
import {deleteSearchParams} from 'modules/utils/filter';
import {useLocation, useNavigate, type Location} from 'react-router-dom';
import {PAGE_TITLE} from 'modules/constants';
import {notificationsStore} from 'modules/stores/notifications';
import {batchModificationStore} from 'modules/stores/batchModification';
import {ProcessDefinitionKeyContext} from '../processDefinitionKeyContext';
import {useFilters} from 'modules/hooks/useFilters';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {reaction} from 'mobx';
import {tracking} from 'modules/tracking';

type LocationType = Omit<Location, 'state'> & {
  state: {refreshContent?: boolean};
};

const ListView: React.FC = observer(() => {
  const location = useLocation() as LocationType;
  const navigate = useNavigate();
  const {getFilters} = useFilters();

  const filters = getFilters();

  const {process, tenant, version} = filters;
  const {
    state: {status: processesStatus},
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
    processesStore.fetchProcesses();

    document.title = PAGE_TITLE.INSTANCES;

    return () => {
      processInstancesSelectionStore.reset();
      processesStore.reset();
    };
  }, []);

  useEffect(() => {
    processInstancesSelectionStore.resetState();
  }, [filtersJSON]);

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
        bottomPanel={<InstancesTableWrapper />}
        frame={{
          isVisible: batchModificationStore.state.isEnabled,
          headerTitle: 'Batch Modification Mode',
        }}
      />
    </ProcessDefinitionKeyContext.Provider>
  );
});

export {ListView};
