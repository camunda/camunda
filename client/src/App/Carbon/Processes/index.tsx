/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {InstancesList} from '../Layout/InstancesList';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {Filters} from './Filters';
import {InstancesTable} from './InstancesTable';
import {DiagramPanel} from './DiagramPanel';
import {observer} from 'mobx-react';
import {useEffect} from 'react';
import {processesStore} from 'modules/stores/processes';
import {
  deleteSearchParams,
  getProcessInstanceFilters,
} from 'modules/utils/filter';
import {useLocation, useNavigate, Location} from 'react-router-dom';
import {useNotifications} from 'modules/notifications';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {PAGE_TITLE} from 'modules/constants';

type LocationType = Omit<Location, 'state'> & {
  state: {refreshContent?: boolean};
};

const Processes: React.FC = observer(() => {
  const location = useLocation() as LocationType;
  const navigate = useNavigate();

  const filters = getProcessInstanceFilters(location.search);
  const {process} = filters;
  const {status: processesStatus} = processesStore.state;
  const filtersJSON = JSON.stringify(filters);

  const notifications = useNotifications();

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
      processInstancesSelectionStore.reset();
      processInstancesStore.reset();
      processesStore.reset();
    };
  }, []);

  useEffect(() => {
    processInstancesSelectionStore.resetState();
  }, [filtersJSON]);

  useEffect(() => {
    if (processesStatus === 'fetched') {
      processInstancesStore.fetchProcessInstancesFromFilters();
    }
  }, [location.search, processesStatus]);

  useEffect(() => {
    if (processesStatus === 'fetched') {
      if (
        process !== undefined &&
        processesStore.processes.filter((item) => item.value === process)
          .length === 0
      ) {
        navigate(deleteSearchParams(location, ['process', 'version']));
        notifications.displayNotification('error', {
          headline: `Process could not be found`,
        });
      }
    }
  }, [process, navigate, processesStatus, notifications, location]);

  return (
    <>
      <VisuallyHiddenH1>Operate Process Instances</VisuallyHiddenH1>
      <InstancesList
        filters={<Filters />}
        diagram={<DiagramPanel />}
        instances={<InstancesTable />}
      />
    </>
  );
});

export {Processes};
