/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {PAGE_TITLE} from 'modules/constants';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {DiagramPanel} from './DiagramPanel';
import {ListPanel} from './ListPanel';
import {OperationsPanel} from './OperationsPanel';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processStatisticsStore} from 'modules/stores/processStatistics';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesDiagramStore} from 'modules/stores/processInstancesDiagram';
import {processesStore} from 'modules/stores/processes';
import {Filters} from './Filters';
import {
  getProcessInstanceFilters,
  deleteSearchParams,
} from 'modules/utils/filter';
import {observer} from 'mobx-react';
import {Content, Container, RightContainer} from './styled';
import {useLocation, useNavigate} from 'react-router-dom';
import {useNotifications} from 'modules/notifications';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {ListFooter} from './ListPanel/ListFooter';

const Processes: React.FC = observer(() => {
  const location = useLocation();
  const navigate = useNavigate();

  const filters = getProcessInstanceFilters(location.search);
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
    processInstancesSelectionStore.init();
    processInstancesStore.init(gseUrl !== null);
    processStatisticsStore.init();
    processesStore.fetchProcesses();

    document.title = PAGE_TITLE.INSTANCES;

    return () => {
      processInstancesSelectionStore.reset();
      processInstancesDiagramStore.reset();
      processStatisticsStore.reset();
      processInstancesStore.reset();
      processesStore.reset();
    };
  }, [gseUrl]);

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
      if (processId === undefined) {
        processInstancesDiagramStore.reset();
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
        processInstancesDiagramStore.fetchProcessXml(processId);
      }
    }
  }, [process, processId, navigate, processesStatus, notifications, location]);

  useEffect(() => {
    if (isSingleProcessSelected) {
      processStatisticsStore.fetchProcessStatistics();
    }
  }, [location.search, isSingleProcessSelected]);

  const containerRef = useRef<HTMLDivElement | null>(null);
  const [clientHeight, setClientHeight] = useState(0);

  useEffect(() => {
    setClientHeight(containerRef?.current?.clientHeight ?? 0);
  }, []);

  const panelMinHeight = clientHeight / 4;

  return (
    <Container>
      <VisuallyHiddenH1>Operate Process Instances</VisuallyHiddenH1>
      <Content>
        <Filters />
        <RightContainer ref={containerRef}>
          <ResizablePanel
            panelId="process-instances-vertical-panel"
            direction={SplitDirection.Vertical}
            minHeights={[panelMinHeight, panelMinHeight]}
          >
            <DiagramPanel />
            <ListPanel />
          </ResizablePanel>
          <ListFooter />
        </RightContainer>
      </Content>
      <OperationsPanel />
    </Container>
  );
});

export {Processes};
