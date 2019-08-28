/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {withData} from 'modules/DataManager';
import SplitPane from 'modules/components/SplitPane';
import {EXPAND_STATE} from 'modules/constants';

import List from './List';
import ListFooter from './ListFooter';

import {withPoll} from 'modules/contexts/InstancesPollContext';

import {getInstancesWithActiveOperations} from 'modules/utils/instance';
import * as Styled from './styled';

class ListPanel extends React.Component {
  static propTypes = {
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    filter: PropTypes.object.isRequired,
    filterCount: PropTypes.number.isRequired,

    instances: PropTypes.array.isRequired,
    sorting: PropTypes.object.isRequired,
    onSort: PropTypes.func.isRequired,
    firstElement: PropTypes.number.isRequired,
    onFirstElementChange: PropTypes.func.isRequired,
    onWorkflowInstancesRefresh: PropTypes.func
  };

  constructor(props) {
    super(props);

    this.state = {
      entriesPerPage: 0,
      instancesLoaded: false
    };

    this.subscriptions = {
      LOAD_STATE_INSTANCES: response => {
        if (response.state === 'LOADING') {
          this.setState({instancesLoaded: false});
        }

        if (response.state === 'LOADED') {
          this.setState({instancesLoaded: true});
        }
      }
    };
  }

  componentDidMount() {
    this.props.dataManager.subscribe(this.subscriptions);
  }

  componentDidUpdate(prevProps, prevState) {
    const hasListChanged =
      !prevState.instancesLoaded && this.state.instancesLoaded;

    const hasEntriesPerPageChanged =
      this.state.instancesLoaded &&
      this.state.entriesPerPage !== prevState.entriesPerPage;
    const isListExpanded = this.state.entriesPerPage > prevState.entriesPerPage;

    if (hasListChanged) {
      if (prevProps.instances.length) {
        const prevActiveInstances = prevProps.instances
          .slice(0, prevState.entriesPerPage)
          .filter(item => this.props.polling.ids.includes(item.id));

        // remove instances with active ops.  from previous page from polling
        Boolean(prevActiveInstances.length) &&
          this.props.polling.removeIds(
            prevActiveInstances.map(instance => instance.id)
          );
      }

      this.checkForInstancesWithActiveOperations();
    }

    // this.props.instances does not change, so hasListChanged = false
    if (hasEntriesPerPageChanged) {
      // fetch the list again when expanding the list panel
      // https://app.camunda.com/jira/browse/OPE-395
      if (isListExpanded) {
        this.props.onWorkflowInstancesRefresh();
      } else {
        // list is collapsed
        const activeIds = this.props.polling.ids;

        const activeInstancesNotInView = this.props.instances
          .slice(this.state.entriesPerPage) // get hidden ids from collapse
          .filter(item => activeIds.includes(item.id)) // get only active ids
          .map(item => item.id);

        if (Boolean(activeInstancesNotInView.length)) {
          this.props.polling.removeIds(activeInstancesNotInView);
        }
      }
    }
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  checkForInstancesWithActiveOperations = () => {
    const instancesInView = this.props.instances.slice(
      0,
      this.state.entriesPerPage
    );
    const instancesWithActiveOperations = getInstancesWithActiveOperations(
      instancesInView
    );
    if (instancesWithActiveOperations.length > 0) {
      this.props.polling.addIds(
        instancesWithActiveOperations.map(instance => instance.id)
      );
    }
  };

  handleActionButtonClick = instance => {
    this.props.polling.addIds([instance.id]);
  };

  render() {
    const {
      filter,
      filterCount,
      onSort,
      onFirstElementChange,
      onWorkflowInstancesRefresh,
      ...paneProps
    } = this.props;
    const isListEmpty = this.props.instances.length === 0;

    return (
      <SplitPane.Pane {...paneProps} hasShiftableControls>
        <SplitPane.Pane.Header>Instances</SplitPane.Pane.Header>
        <Styled.PaneBody>
          <List
            data={this.props.instances}
            filterCount={filterCount}
            filter={filter}
            sorting={this.props.sorting}
            onSort={this.props.onSort}
            expandState={this.props.expandState}
            onEntriesPerPageChange={entriesPerPage =>
              this.setState({entriesPerPage})
            }
            isDataLoaded={this.props.instancesLoaded}
            onActionButtonClick={this.handleActionButtonClick}
          />
        </Styled.PaneBody>
        <SplitPane.Pane.Footer>
          {!isListEmpty && (
            <ListFooter
              filterCount={filterCount}
              perPage={this.state.entriesPerPage}
              firstElement={this.props.firstElement}
              onFirstElementChange={this.props.onFirstElementChange}
            />
          )}
        </SplitPane.Pane.Footer>
      </SplitPane.Pane>
    );
  }
}

const WrappedListPanel = withData(withPoll(ListPanel));
WrappedListPanel.WrappedComponent = ListPanel;

export default WrappedListPanel;
