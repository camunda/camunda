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
import {workflowStatisticsStore} from 'modules/stores/workflowStatistics';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {workflowsStore} from 'modules/stores/workflows';
import {Filters} from './Filters';
import {getFilters} from 'modules/utils/filter';
import {observer} from 'mobx-react';
import * as Styled from './styled';
import {useLocation} from 'react-router-dom';

const Instances: React.FC = observer(() => {
  const location = useLocation();
  const filters = getFilters(location.search);
  const {workflow, version} = filters;
  const workflowId =
    workflow !== undefined && version !== undefined
      ? workflowsStore.versionsByWorkflow?.[workflow]?.[parseInt(version) - 1]
          ?.id
      : undefined;
  const {status: workflowsStatus} = workflowsStore.state;
  const isSingleWorkflowSelected = workflowId !== undefined;
  const filtersJSON = JSON.stringify(filters);

  useEffect(() => {
    instanceSelectionStore.init();
    instancesStore.init();
    workflowStatisticsStore.init();
    workflowsStore.fetchWorkflows();
    document.title = PAGE_TITLE.INSTANCES;

    return () => {
      instanceSelectionStore.reset();
      instancesDiagramStore.reset();
      workflowStatisticsStore.reset();
      instancesStore.reset();
      workflowsStore.reset();
    };
  }, []);

  useEffect(() => {
    instanceSelectionStore.resetState();
  }, [filtersJSON]);

  useEffect(() => {
    if (workflowsStatus === 'fetched') {
      instancesStore.fetchInstancesFromFilters();
    }
  }, [location.search, workflowsStatus]);

  useEffect(() => {
    if (workflowId === undefined) {
      instancesDiagramStore.resetDiagramModel();
      workflowStatisticsStore.reset();
    } else {
      instancesDiagramStore.fetchWorkflowXml(workflowId);
    }
  }, [workflowId]);

  useEffect(() => {
    if (isSingleWorkflowSelected) {
      workflowStatisticsStore.fetchWorkflowStatistics();
    }
  }, [location.search, isSingleWorkflowSelected]);

  return (
    <Styled.Instances>
      <VisuallyHiddenH1>Camunda Operate Instances</VisuallyHiddenH1>
      <Styled.Content>
        <Styled.FilterSection>
          <Filters />
        </Styled.FilterSection>
        <Styled.SplitPane
          titles={{top: 'Workflow', bottom: 'Instances'}}
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
