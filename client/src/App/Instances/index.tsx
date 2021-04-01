/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
import {PAGE_TITLE} from 'modules/constants';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {DiagramPanel} from './DiagramPanel';
import {ListPanel} from './ListPanel';
import OperationsPanel from './OperationsPanel';
import {instancesStore} from 'modules/stores/instances';
import {processStatisticsStore} from 'modules/stores/processStatistics';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {processesStore} from 'modules/stores/processes';
import {Filters} from './Filters';
import {getFilters} from 'modules/utils/filter';
import {observer} from 'mobx-react';
import * as Styled from './styled';
import {useLocation} from 'react-router-dom';

const Instances: React.FC = observer(() => {
  const location = useLocation();
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
    if (processId === undefined) {
      instancesDiagramStore.resetDiagramModel();
      processStatisticsStore.reset();
    } else {
      instancesDiagramStore.fetchProcessXml(processId);
    }
  }, [processId]);

  useEffect(() => {
    if (isSingleProcessSelected) {
      processStatisticsStore.fetchProcessStatistics();
    }
  }, [location.search, isSingleProcessSelected]);

  return (
    <Styled.Instances>
      <VisuallyHiddenH1>Camunda Operate Instances</VisuallyHiddenH1>
      <Styled.Content>
        <Styled.FilterSection>
          <Filters />
        </Styled.FilterSection>
        <Styled.SplitPane
          titles={{top: 'Process', bottom: 'Instances'}}
          expandedPaneId="instancesExpandedPaneId"
        >
          <DiagramPanel />
          <ListPanel />
        </Styled.SplitPane>
      </Styled.Content>
      <OperationsPanel />
    </Styled.Instances>
  );
});

export {Instances};
