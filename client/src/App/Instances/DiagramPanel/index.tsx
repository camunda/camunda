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

type Props = {
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
};

const DiagramPanel = observer(
  class DiagramPanel extends React.Component<Props, {}> {
    renderMessage = (type: unknown) => {
      const message = {
        NoWorkflow: `There is no Workflow selected.\n To see a diagram, select a Workflow in the Filters panel.`,
        NoVersion: `There is more than one version selected for Workflow "${filtersStore.workflowName}".\n To see a diagram, select a single version.`,
      };
      return (
        <Styled.EmptyMessageWrapper>
          {/* @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message */}
          <Styled.DiagramEmptyMessage message={message[type]} />
        </Styled.EmptyMessageWrapper>
      );
    };

    render() {
      const {
        isLoading: areStateDefinitionsLoading,
        diagramModel,
      } = instancesDiagramStore.state;

      const {selectableIds} = instancesDiagramStore;
      const {filter} = filtersStore.state;
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'activityId' does not exist on type '{}'.
      const selectedFlowNodeId = selectableIds.includes(filter.activityId)
        ? // @ts-expect-error ts-migrate(2339) FIXME: Property 'activityId' does not exist on type '{}'.
          filter.activityId
        : undefined;
      const {statistics} = workflowStatisticsStore.state;
      return (
        <SplitPane.Pane {...this.props}>
          <Styled.PaneHeader>
            <span>{filtersStore.workflowName}</span>
          </Styled.PaneHeader>
          <SplitPane.Pane.Body style={{position: 'relative'}}>
            {/* @ts-expect-error ts-migrate(2339) FIXME: Property 'isLoading' does not exist on type 'Workf... Remove this comment to see the full error message */}
            {(workflowStatisticsStore.isLoading ||
              areStateDefinitionsLoading) && (
              <SpinnerSkeleton data-testid="spinner" />
            )}
            {filtersStore.isNoWorkflowSelected &&
              this.renderMessage('NoWorkflow')}
            {filtersStore.isNoVersionSelected
              ? this.renderMessage('NoVersion')
              : // @ts-expect-error ts-migrate(2339) FIXME: Property 'definitions' does not exist on type 'nev... Remove this comment to see the full error message
                diagramModel?.definitions && (
                  <Diagram
                    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
                    definitions={diagramModel.definitions}
                    onFlowNodeSelection={(activityId) => {
                      filtersStore.setFilter({
                        // @ts-expect-error
                        ...filtersStore.state.filter,
                        activityId: activityId ? activityId : '',
                      });
                    }}
                    flowNodesStatistics={statistics}
                    selectedFlowNodeId={selectedFlowNodeId}
                    selectableFlowNodes={selectableIds}
                    expandState={this.props.expandState}
                  />
                )}
          </SplitPane.Pane.Body>
        </SplitPane.Pane>
      );
    }
  }
);

export {DiagramPanel};
