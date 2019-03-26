/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import PropTypes from 'prop-types';

import {DEFAULT_FILTER} from 'modules/constants';

import {isEmpty, sortBy} from 'lodash';

import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {
  SelectionProvider,
  SelectionConsumer
} from 'modules/contexts/SelectionContext';
import {getInstancesIdsFromSelections} from 'modules/contexts/SelectionContext/service';
import {InstancesPollProvider} from 'modules/contexts/InstancesPollContext';

import Header from '../Header';
import ListView from './ListView';
import Filters from './Filters';
import Selections from './Selections';

import {
  getEmptyDiagramMessage,
  getTaskNodes,
  getWorkflowByVersionFromFilter,
  getWorkflowNameFromFilter
} from './service';
import * as Styled from './styled.js';

export default class Instances extends Component {
  static propTypes = {
    filter: PropTypes.shape({
      workflow: PropTypes.string,
      version: PropTypes.string,
      active: PropTypes.bool,
      ids: PropTypes.string,
      startDate: PropTypes.string,
      endDate: PropTypes.string,
      errorMessage: PropTypes.string,
      incidents: PropTypes.bool,
      canceled: PropTypes.bool,
      completed: PropTypes.bool,
      activityId: PropTypes.string
    }).isRequired,
    filterCount: PropTypes.number.isRequired,
    groupedWorkflows: PropTypes.object.isRequired,
    workflowInstances: PropTypes.array.isRequired,
    workflowInstancesLoaded: PropTypes.bool.isRequired,
    firstElement: PropTypes.number.isRequired,
    onFirstElementChange: PropTypes.func.isRequired,
    sorting: PropTypes.object.isRequired,
    onSort: PropTypes.func.isRequired,
    onFilterChange: PropTypes.func.isRequired,
    onFilterReset: PropTypes.func.isRequired,
    diagramModel: PropTypes.shape({
      bpmnElements: PropTypes.object,
      definitions: PropTypes.object
    }).isRequired,
    statistics: PropTypes.array.isRequired,
    onWorkflowInstancesRefresh: PropTypes.func
  };

  handleFlowNodeSelection = flowNodeId => {
    this.props.onFilterChange({activityId: flowNodeId});
  };

  render() {
    const {filter, groupedWorkflows} = this.props;
    const currentWorkflowByVersion = getWorkflowByVersionFromFilter({
      filter,
      groupedWorkflows
    });
    const workflowName = getWorkflowNameFromFilter({filter, groupedWorkflows});
    const activityIds = getTaskNodes(this.props.diagramModel.bpmnElements).map(
      item => {
        return {value: item.id, label: item.name};
      }
    );

    return (
      <SelectionProvider
        groupedWorkflows={this.props.groupedWorkflows}
        filter={this.props.filter}
      >
        <SelectionConsumer>
          {selections => (
            <InstancesPollProvider
              onWorkflowInstancesRefresh={this.props.onWorkflowInstancesRefresh}
              onSelectionsRefresh={selections.onInstancesInSelectionsRefresh}
              visibleIdsInListView={this.props.workflowInstances.map(x => x.id)}
              visibleIdsInSelections={getInstancesIdsFromSelections(
                selections.selections
              )}
            >
              <Header
                active="instances"
                filter={this.props.filter}
                filterCount={this.props.filterCount}
                onFilterReset={this.props.onFilterReset}
              />
              <Styled.Instances>
                <VisuallyHiddenH1>Camunda Operate Instances</VisuallyHiddenH1>
                <Styled.Content>
                  <Styled.Filters>
                    <Filters
                      activityIds={sortBy(activityIds, item =>
                        item.label.toLowerCase()
                      )}
                      groupedWorkflows={this.props.groupedWorkflows}
                      filter={this.props.filter}
                      filterCount={this.props.filterCount}
                      onFilterReset={() =>
                        this.props.onFilterReset(DEFAULT_FILTER)
                      }
                      onFilterChange={this.props.onFilterChange}
                    />
                  </Styled.Filters>

                  <Styled.Center
                    titles={{top: 'Workflow', bottom: 'Instances'}}
                  >
                    <Styled.Pane>
                      <Styled.PaneHeader>
                        <span data-test="instances-diagram-title">
                          {workflowName}
                        </span>
                      </Styled.PaneHeader>
                      <SplitPane.Pane.Body>
                        {!this.props.filter.workflow && (
                          <Styled.EmptyMessageWrapper>
                            <Styled.DiagramEmptyMessage
                              message={`There is no Workflow selected.\n
To see a diagram, select a Workflow in the Filters panel.`}
                              data-test="data-test-noWorkflowMessage"
                            />
                          </Styled.EmptyMessageWrapper>
                        )}
                        {this.props.filter.version === 'all' && (
                          <Styled.EmptyMessageWrapper>
                            <Styled.DiagramEmptyMessage
                              message={getEmptyDiagramMessage(workflowName)}
                              data-test="data-test-allVersionsMessage"
                            />
                          </Styled.EmptyMessageWrapper>
                        )}
                        {!isEmpty(currentWorkflowByVersion) &&
                          this.props.diagramModel.definitions && (
                            <Diagram
                              flowNodesStatistics={this.props.statistics}
                              onFlowNodeSelection={this.handleFlowNodeSelection}
                              selectedFlowNodeId={this.props.filter.activityId}
                              selectableFlowNodes={activityIds.map(
                                item => item.value
                              )}
                              definitions={this.props.diagramModel.definitions}
                            />
                          )}
                      </SplitPane.Pane.Body>
                    </Styled.Pane>

                    <ListView
                      instances={this.props.workflowInstances}
                      instancesLoaded={this.props.workflowInstancesLoaded}
                      filter={this.props.filter}
                      filterCount={this.props.filterCount}
                      onSort={this.props.onSort}
                      sorting={this.props.sorting}
                      firstElement={this.props.firstElement}
                      onFirstElementChange={this.props.onFirstElementChange}
                      onWorkflowInstancesRefresh={
                        this.props.onWorkflowInstancesRefresh
                      }
                    />
                  </Styled.Center>
                </Styled.Content>
                <Selections />
              </Styled.Instances>
            </InstancesPollProvider>
          )}
        </SelectionConsumer>
      </SelectionProvider>
    );
  }
}
