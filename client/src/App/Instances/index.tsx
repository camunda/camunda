/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {PAGE_TITLE} from 'modules/constants';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {DiagramPanel} from './DiagramPanel';
import {ListPanel} from './ListPanel';
import {OperationsPanel} from './OperationsPanel';
import {instancesStore} from 'modules/stores/instances';
import {processStatisticsStore} from 'modules/stores/processStatistics';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {processesStore} from 'modules/stores/processes';
import {Filters} from './Filters';
import {getFilters, deleteSearchParams} from 'modules/utils/filter';
import {observer} from 'mobx-react';
import {Content, Container, Separator, SplitPane} from './styled';
import {useLocation, useNavigate} from 'react-router-dom';
import {useNotifications} from 'modules/notifications';

const Instances: React.FC = observer(() => {
  const location = useLocation();
  const navigate = useNavigate();

  const filters = getFilters(location.search);
  const {process, version} = filters;
  const processId =
    process !== undefined && version !== undefined
      ? processesStore.versionsByProcess?.[process]?.[parseInt(version) - 1]?.id
      : undefined;
  const {status: processesStatus} = processesStore.state;
  const isSingleProcessSelected = processId !== undefined;
  const filtersJSON = JSON.stringify(filters);
  const searchParams = new URLSearchParams(location.search);
  const gseUrl = searchParams.get('gseUrl');

  const notifications = useNotifications();

  useEffect(() => {
    instanceSelectionStore.init();
    instancesStore.init(gseUrl !== null);
    processStatisticsStore.init();
    processesStore.fetchProcesses();

    document.title = PAGE_TITLE.INSTANCES;

    return () => {
      instanceSelectionStore.reset();
      instancesDiagramStore.reset();
      processStatisticsStore.reset();
      instancesStore.reset();
      processesStore.reset();
    };
  }, [gseUrl]);

  useEffect(() => {
    instanceSelectionStore.resetState();
  }, [filtersJSON]);

  useEffect(() => {
    if (processesStatus === 'fetched') {
      instancesStore.fetchInstancesFromFilters();
    }
  }, [location.search, processesStatus]);

  useEffect(() => {
    if (processesStatus === 'fetched') {
      if (processId === undefined) {
        instancesDiagramStore.reset();
        processStatisticsStore.reset();

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
      } else {
        instancesDiagramStore.fetchProcessXml(processId);
      }
    }
  }, [process, processId, navigate, processesStatus, notifications, location]);

  useEffect(() => {
    if (isSingleProcessSelected) {
      processStatisticsStore.fetchProcessStatistics();
    }
  }, [location.search, isSingleProcessSelected]);

  return (
    <Container>
      <VisuallyHiddenH1>Operate Instances</VisuallyHiddenH1>
      <Content>
        <Filters />
        <Separator />
        <SplitPane
          titles={{top: 'Process', bottom: 'Instances'}}
          expandedPaneId="instancesExpandedPaneId"
        >
          <DiagramPanel />
          <ListPanel />
        </SplitPane>
      </Content>
      <OperationsPanel />
    </Container>
  );
});

export {Instances};
