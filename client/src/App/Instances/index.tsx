/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';

import {DEFAULT_FILTER_CONTROLLED_VALUES, PAGE_TITLE} from 'modules/constants';

import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';

import {DiagramPanel} from './DiagramPanel';
import {ListPanel} from './ListPanel';
import Filters from './Filters';
import OperationsPanel from './OperationsPanel';
import {filtersStore} from 'modules/stores/filters';
import {instancesStore} from 'modules/stores/instances';
import {workflowStatisticsStore} from 'modules/stores/workflowStatistics';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {workflowsStore} from 'modules/stores/workflows';
import {Filters as FiltersV2} from './FiltersV2';
import {getFilters, IS_FILTERS_V2} from 'modules/utils/filter';
import {Location, History} from 'history';

import {observer} from 'mobx-react';
import * as Styled from './styled';

type Props = {
  location: Location;
  history: History;
};

const Instances: React.FC<Props> = observer(({location, history}) => {
  const {workflow, workflowVersion} = getFilters(location.search);
  const workflowId =
    workflow !== undefined && workflowVersion !== undefined
      ? workflowsStore.versionsByWorkflow?.[workflow]?.[
          parseInt(workflowVersion) - 1
        ]?.id
      : undefined;
  const {status: workflowsStatus} = workflowsStore.state;
  const isSingleWorkflowSelected = workflowId !== undefined;

  useEffect(() => {
    instanceSelectionStore.init();
    instancesStore.init();
    workflowStatisticsStore.init();
    document.title = PAGE_TITLE.INSTANCES;

    if (IS_FILTERS_V2) {
      workflowsStore.fetchWorkflows();
    } else {
      filtersStore.init();
      instancesDiagramStore.init();
    }
    return () => {
      filtersStore.reset();
      instanceSelectionStore.reset();
      instancesDiagramStore.reset();
      workflowStatisticsStore.reset();
      instancesStore.reset();

      if (IS_FILTERS_V2) {
        workflowsStore.reset();
      }
    };
  }, []);

  useEffect(() => {
    if (!IS_FILTERS_V2) {
      filtersStore.setUrlParameters(history, location);
    }
  }, [history, location]);

  useEffect(() => {
    if (IS_FILTERS_V2) {
      instanceSelectionStore.resetState();
    }
  }, [location.search]);

  useEffect(() => {
    if (IS_FILTERS_V2 && workflowsStatus === 'fetched') {
      instancesStore.fetchInstancesFromFilters();
    }
  }, [location.search, workflowsStatus]);

  useEffect(() => {
    async function handleWorkflowChange() {
      if (IS_FILTERS_V2) {
        if (workflowId === undefined) {
          instancesDiagramStore.resetDiagramModel();
        } else {
          instancesDiagramStore.fetchWorkflowXml(workflowId);
        }
      }
    }

    handleWorkflowChange();
  }, [workflowId]);

  useEffect(() => {
    if (IS_FILTERS_V2 && isSingleWorkflowSelected) {
      workflowStatisticsStore.fetchWorkflowStatistics();
    }
  }, [location.search, isSingleWorkflowSelected]);

  return (
    <Styled.Instances>
      <VisuallyHiddenH1>Camunda Operate Instances</VisuallyHiddenH1>
      <Styled.Content>
        <Styled.FilterSection>
          {IS_FILTERS_V2 ? (
            <FiltersV2 />
          ) : (
            <Filters
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={{
                ...DEFAULT_FILTER_CONTROLLED_VALUES,
                ...filtersStore.decodedFilters,
              }}
            />
          )}
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
