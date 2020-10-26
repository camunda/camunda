/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {EXPAND_STATE} from 'modules/constants';
import SplitPane from 'modules/components/SplitPane';
import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';
import Diagram from 'modules/components/Diagram';
import * as Styled from './styled.js';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {workflowStatisticsStore} from 'modules/stores/workflowStatistics';
import {filtersStore} from 'modules/stores/filters';
import {observer} from 'mobx-react';

const DiagramPanel = observer(
  class DiagramPanel extends React.Component {
    static propTypes = {
      expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    };

    renderMessage = (type) => {
      const message = {
        NoWorkflow: `There is no Workflow selected.\n To see a diagram, select a Workflow in the Filters panel.`,
        NoVersion: `There is more than one version selected for Workflow "${filtersStore.workflowName}".\n To see a diagram, select a single version.`,
      };
      return (
        <Styled.EmptyMessageWrapper>
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
      const selectedFlowNodeId = selectableIds.includes(filter.activityId)
        ? filter.activityId
        : undefined;
      const {statistics} = workflowStatisticsStore.state;
      return (
        <SplitPane.Pane {...this.props}>
          <Styled.PaneHeader>
            <span>{filtersStore.workflowName}</span>
          </Styled.PaneHeader>
          <SplitPane.Pane.Body style={{position: 'relative'}}>
            {(workflowStatisticsStore.isLoading ||
              areStateDefinitionsLoading) && (
              <SpinnerSkeleton data-testid="spinner" />
            )}
            {filtersStore.isNoWorkflowSelected &&
              this.renderMessage('NoWorkflow')}
            {filtersStore.isNoVersionSelected
              ? this.renderMessage('NoVersion')
              : diagramModel?.definitions && (
                  <Diagram
                    definitions={diagramModel.definitions}
                    onFlowNodeSelection={(activityId) => {
                      filtersStore.setFilter({
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
