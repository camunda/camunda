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
import {instancesDiagram} from 'modules/stores/instancesDiagram';
import {workflowStatistics} from 'modules/stores/workflowStatistics';
import {filters} from 'modules/stores/filters';
import {observer} from 'mobx-react';

const DiagramPanel = observer(
  class DiagramPanel extends React.Component {
    static propTypes = {
      expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    };

    renderMessage = (type) => {
      const message = {
        NoWorkflow: `There is no Workflow selected.\n To see a diagram, select a Workflow in the Filters panel.`,
        NoVersion: `There is more than one version selected for Workflow "${filters.workflowName}".\n To see a diagram, select a single version.`,
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
      } = instancesDiagram.state;

      const {selectableIds} = instancesDiagram;
      const {filter} = filters.state;
      const selectedFlowNodeId = selectableIds.includes(filter.activityId)
        ? filter.activityId
        : undefined;
      const {statistics} = workflowStatistics.state;
      return (
        <SplitPane.Pane {...this.props}>
          <Styled.PaneHeader>
            <span>{filters.workflowName}</span>
          </Styled.PaneHeader>
          <SplitPane.Pane.Body style={{position: 'relative'}}>
            {(workflowStatistics.isLoading || areStateDefinitionsLoading) && (
              <SpinnerSkeleton data-testid="spinner" />
            )}
            {filters.isNoWorkflowSelected && this.renderMessage('NoWorkflow')}
            {filters.isNoVersionSelected
              ? this.renderMessage('NoVersion')
              : diagramModel?.definitions && (
                  <Diagram
                    definitions={diagramModel.definitions}
                    onFlowNodeSelection={(activityId) => {
                      filters.setFilter({
                        ...filters.state.filter,
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
