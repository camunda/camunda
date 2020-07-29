/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import PropTypes from 'prop-types';

import {
  DEFAULT_FILTER,
  DEFAULT_FILTER_CONTROLLED_VALUES,
} from 'modules/constants';

import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {InstancesPollProvider} from 'modules/contexts/InstancesPollContext';

import DiagramPanel from './DiagramPanel';
import ListPanel from './ListPanel';
import Filters from './Filters';
import OperationsPanel from './OperationsPanel';

import {getWorkflowNameFromFilter} from './service';

import * as Styled from './styled.js';

class Instances extends Component {
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
        value: PropTypes.string,
      }),
      batchOperationId: PropTypes.string,
    }).isRequired,
    resetFilters: PropTypes.bool,
    afterFilterReset: PropTypes.func,
    groupedWorkflows: PropTypes.object.isRequired,
    workflowInstances: PropTypes.array.isRequired,
    initialLoad: PropTypes.bool,
    instancesLoaded: PropTypes.bool,
    firstElement: PropTypes.number.isRequired,
    onFirstElementChange: PropTypes.func.isRequired,
    sorting: PropTypes.object.isRequired,
    onSort: PropTypes.func.isRequired,
    onFilterChange: PropTypes.func.isRequired,
    onFilterReset: PropTypes.func.isRequired,
    onFlowNodeSelection: PropTypes.func.isRequired,
    statistics: PropTypes.array.isRequired,
    onInstancesClick: PropTypes.func.isRequired,
  };

  render() {
    const {
      filter,
      groupedWorkflows,
      workflowInstances,
      onFilterReset,
      onFilterChange,
      onFlowNodeSelection,
      statistics,
      onSort,
      sorting,
      firstElement,
      onFirstElementChange,
      resetFilters,
      afterFilterReset,
      initialLoad,
      instancesLoaded,
    } = this.props;

    const workflowName = getWorkflowNameFromFilter({
      filter,
      groupedWorkflows,
    });
    return (
      <InstancesPollProvider
        visibleIdsInListPanel={workflowInstances.map(({id}) => id)}
        filter={filter}
      >
        <Styled.Instances>
          <VisuallyHiddenH1>Camunda Operate Instances</VisuallyHiddenH1>
          <Styled.Content>
            <Styled.FilterSection>
              <Filters
                groupedWorkflows={groupedWorkflows}
                filter={{
                  ...DEFAULT_FILTER_CONTROLLED_VALUES,
                  ...filter,
                }}
                resetFilters={resetFilters}
                afterFilterReset={afterFilterReset}
                onFilterReset={() => onFilterReset(DEFAULT_FILTER)}
                onFilterChange={onFilterChange}
              />
            </Styled.FilterSection>
            <Styled.SplitPane
              titles={{top: 'Workflow', bottom: 'Instances'}}
              expandedPaneId="instancesExpandedPaneId"
            >
              <DiagramPanel
                workflowName={workflowName}
                onFlowNodeSelection={onFlowNodeSelection}
                noWorkflowSelected={!filter.workflow}
                noVersionSelected={filter.version === 'all'}
                flowNodesStatistics={statistics}
                activityId={filter.activityId}
              />
              <ListPanel
                instances={workflowInstances}
                initialLoad={initialLoad}
                instancesLoaded={instancesLoaded}
                filter={filter}
                onSort={onSort}
                sorting={sorting}
                firstElement={firstElement}
                onFirstElementChange={onFirstElementChange}
              />
            </Styled.SplitPane>
          </Styled.Content>
          <OperationsPanel onInstancesClick={this.props.onInstancesClick} />
        </Styled.Instances>
      </InstancesPollProvider>
    );
  }
}

export {Instances};
