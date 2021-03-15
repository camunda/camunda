/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import SplitPane from 'modules/components/SplitPane';
import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';
import Diagram from 'modules/components/Diagram';
import * as Styled from './styled';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {workflowStatisticsStore} from 'modules/stores/workflowStatistics';
import {filtersStore} from 'modules/stores/filters';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {useHistory} from 'react-router-dom';
import {Location} from 'history';
import {getFilters} from 'modules/utils/filter';
import {IS_FILTERS_V2} from 'modules/feature-flags';

const Message: React.FC = ({children}) => {
  return (
    <Styled.EmptyMessageWrapper>
      <Styled.DiagramEmptyMessage message={children} />
    </Styled.EmptyMessageWrapper>
  );
};

function deleteSearchParam(location: Location, param: string) {
  const params = new URLSearchParams(location.search);

  params.delete(param);

  return {
    ...location,
    search: params.toString(),
  };
}

function setSearchParam(
  location: Location,
  [key, value]: [key: string, value: string]
) {
  const params = new URLSearchParams(location.search);

  params.set(key, value);

  return {
    ...location,
    search: params.toString(),
  };
}

type Props = {
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
};

const DiagramPanel: React.FC<Props> = observer((props) => {
  const history = useHistory();
  const {status, diagramModel} = instancesDiagramStore.state;
  const {selectableIds} = instancesDiagramStore;
  const {filter} = filtersStore.state;
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'activityId' does not exist on type '{}'.
  const selectedFlowNodeId = selectableIds.includes(filter.activityId)
    ? // @ts-expect-error ts-migrate(2339) FIXME: Property 'activityId' does not exist on type '{}'.
      filter.activityId
    : undefined;
  const {statistics} = workflowStatisticsStore.state;
  const {workflow, workflowVersion, flowNodeId} = getFilters(
    history.location.search
  );
  const isNoWorkflowSelected = IS_FILTERS_V2
    ? workflow === undefined
    : filtersStore.isNoWorkflowSelected;
  const isNoVersionSelected = IS_FILTERS_V2
    ? workflowVersion === 'all'
    : filtersStore.isNoVersionSelected;

  return (
    <SplitPane.Pane {...props}>
      <Styled.PaneHeader>
        <span>{filtersStore.workflowName}</span>
      </Styled.PaneHeader>
      <SplitPane.Pane.Body style={{position: 'relative'}}>
        {(workflowStatisticsStore.state.isLoading || status === 'fetching') && (
          <SpinnerSkeleton data-testid="spinner" />
        )}
        {status === 'error' && (
          <Message>
            <StatusMessage variant="error">
              Diagram could not be fetched
            </StatusMessage>
          </Message>
        )}
        {isNoWorkflowSelected && (
          <Message>
            {
              'There is no Workflow selected\n To see a Diagram, select a Workflow in the Filters panel'
            }
          </Message>
        )}
        {isNoVersionSelected ? (
          <Message>
            {`There is more than one Version selected for Workflow "${filtersStore.workflowName}"
               To see a Diagram, select a single Version`}
          </Message>
        ) : null}
        {/* @ts-expect-error ts-migrate(2339) FIXME: Property 'definitions' does not exist on type 'nev... Remove this comment to see the full error message */}
        {!isNoVersionSelected && diagramModel?.definitions ? (
          <Diagram
            // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
            definitions={diagramModel.definitions}
            onFlowNodeSelection={(flowNodeId) => {
              if (IS_FILTERS_V2) {
                if (flowNodeId === null || flowNodeId === undefined) {
                  history.push(
                    deleteSearchParam(history.location, 'flowNodeId')
                  );
                } else {
                  history.push(
                    setSearchParam(history.location, ['flowNodeId', flowNodeId])
                  );
                }
              } else {
                filtersStore.setFilter({
                  // @ts-expect-error
                  ...filtersStore.state.filter,
                  activityId: flowNodeId ?? '',
                });
              }
            }}
            flowNodesStatistics={statistics}
            selectedFlowNodeId={IS_FILTERS_V2 ? flowNodeId : selectedFlowNodeId}
            selectableFlowNodes={selectableIds}
            expandState={props.expandState}
          />
        ) : null}
      </SplitPane.Pane.Body>
    </SplitPane.Pane>
  );
});

export {DiagramPanel};
