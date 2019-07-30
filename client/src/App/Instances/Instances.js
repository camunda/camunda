/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import PropTypes from 'prop-types';

import {DEFAULT_FILTER} from 'modules/constants';
import {isEmpty} from 'lodash';

import Diagram from 'modules/components/Diagram';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {
  SelectionProvider,
  SelectionConsumer
} from 'modules/contexts/SelectionContext';
import {getInstancesIdsFromSelections} from 'modules/contexts/SelectionContext/service';
import {InstancesPollProvider} from 'modules/contexts/InstancesPollContext';

import Header from '../Header';
import TopPane from './TopPane';
import ListView from './ListView';
import Filters from './Filters';
import Selections from './Selections';

import {
  getWorkflowByVersionFromFilter,
  getWorkflowNameFromFilter
} from './service';

import {getFlowNodes} from 'modules/utils/flowNodes';
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
      activityId: PropTypes.string,
      variable: PropTypes.shape({
        name: PropTypes.string,
        value: PropTypes.string
      })
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
    onFlowNodeSelection: PropTypes.func.isRequired,
    diagramModel: PropTypes.shape({
      bpmnElements: PropTypes.object,
      definitions: PropTypes.object
    }).isRequired,
    statistics: PropTypes.array.isRequired,
    onWorkflowInstancesRefresh: PropTypes.func
  };

  render() {
    const {filter, groupedWorkflows} = this.props;
    const currentWorkflowByVersion = getWorkflowByVersionFromFilter({
      filter,
      groupedWorkflows
    });

    const workflowName = getWorkflowNameFromFilter({filter, groupedWorkflows});

    const {ids: selectableIds, flowNodes: selectableFlowNodes} = getFlowNodes(
      this.props.diagramModel.bpmnElements
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
                  <Styled.FilterSection>
                    <Filters
                      selectableFlowNodes={selectableFlowNodes}
                      groupedWorkflows={this.props.groupedWorkflows}
                      filter={this.props.filter}
                      filterCount={this.props.filterCount}
                      onFilterReset={() =>
                        this.props.onFilterReset(DEFAULT_FILTER)
                      }
                      onFilterChange={this.props.onFilterChange}
                    />
                  </Styled.FilterSection>

                  <Styled.SplitPane
                    titles={{top: 'Workflow', bottom: 'Instances'}}
                  >
                    <TopPane
                      workflowName={workflowName}
                      renderNoWorkflowMessage={!filter.workflow}
                      renderNoVersionMessage={filter.version === 'all'}
                      renderChildren={
                        !isEmpty(currentWorkflowByVersion) &&
                        !!this.props.diagramModel.definitions
                      }
                    >
                      <Diagram
                        definitions={this.props.diagramModel.definitions}
                        flowNodesStatistics={this.props.statistics}
                        onFlowNodeSelection={this.props.onFlowNodeSelection}
                        selectedFlowNodeId={this.props.filter.activityId}
                        selectableFlowNodes={selectableIds}
                      />
                    </TopPane>

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
                  </Styled.SplitPane>
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
