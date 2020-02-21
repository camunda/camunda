/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {
  LOADING_STATE,
  EXPAND_STATE,
  SUBSCRIPTION_TOPIC
} from 'modules/constants';

import {getInstancesWithActiveOperations} from 'modules/utils/instance';

import {withPoll} from 'modules/contexts/InstancesPollContext';
import {withData} from 'modules/DataManager';
import {InstanceSelectionProvider} from 'modules/contexts/InstanceSelectionContext';

import SplitPane from 'modules/components/SplitPane';

import List from './List';
import ListFooter from './ListFooter';
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
    rowsToDisplay: PropTypes.number,
    dataManager: PropTypes.shape({
      subscribe: PropTypes.func,
      unsubscribe: PropTypes.func,
      update: PropTypes.func
    }),
    polling: PropTypes.shape({
      active: PropTypes.object,
      removeIds: PropTypes.func,
      addIds: PropTypes.func
    })
  };

  constructor(props) {
    super(props);

    this.state = {
      entriesPerPage: 0,
      instancesLoaded: false,
      initialLoad: true
    };

    this.subscriptions = {
      LOAD_LIST_INSTANCES: response => {
        if (response.state === LOADING_STATE.LOADING) {
          this.setState({instancesLoaded: false});
        }

        if (response.state === LOADING_STATE.LOADED) {
          this.setState({initialLoad: false, instancesLoaded: true});
        }
      }
    };
  }

  componentDidMount() {
    this.props.dataManager.subscribe(this.subscriptions);
  }

  componentDidUpdate(prevProps, prevState) {
    const {dataManager} = this.props;
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
          .filter(item => this.props.polling.active.has(item.id));

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
      if (isListExpanded) {
        dataManager.update({
          endpoints: [SUBSCRIPTION_TOPIC.LOAD_LIST_INSTANCES],
          topic: SUBSCRIPTION_TOPIC.REFRESH_AFTER_OPERATION
        });
      } else {
        // list is collapsed
        const activeIds = this.props.polling.active;

        const activeInstancesNotInView = this.props.instances
          .slice(this.state.entriesPerPage) // get hidden ids from collapse
          .filter(item => activeIds.has(item.id)) // get only active ids
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

  getEmptyListMessage = () => {
    const {active, incidents, completed, canceled} = this.props.filter;

    let msg = 'There are no instances matching this filter set.';

    if (!active && !incidents && !completed && !canceled) {
      msg += '\n To see some results, select at least one instance state.';
    }

    return msg;
  };

  renderContent(isListEmpty) {
    if (!isListEmpty) {
      return <List.Body />;
    } else if (this.state.initialLoad) {
      return <List.Skeleton rowsToDisplay={this.props.rowsToDisplay} />;
    } else {
      return <List.Message message={this.getEmptyListMessage()} />;
    }
  }

  renderSpinner() {
    return (
      !this.state.instancesLoaded &&
      !this.state.initialLoad &&
      (() => <Styled.Spinner />)
    );
  }

  render() {
    const {
      filter,
      filterCount,
      onSort,
      onFirstElementChange,
      ...paneProps
    } = this.props;
    const isListEmpty = this.props.instances.length === 0;
    const isExpanded = this.props.expandState !== EXPAND_STATE.COLLAPSED;

    return (
      <InstanceSelectionProvider>
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
              rowsToDisplay={this.state.entriesPerPage}
              isDataLoaded={this.state.instancesLoaded}
              onActionButtonClick={this.handleActionButtonClick}
              Overlay={this.renderSpinner()}
            >
              <List.Header />
              {this.renderContent(isListEmpty)}
            </List>
          </Styled.PaneBody>
          <SplitPane.Pane.Footer>
            <ListFooter
              filterCount={filterCount}
              perPage={this.state.entriesPerPage}
              firstElement={this.props.firstElement}
              onFirstElementChange={this.props.onFirstElementChange}
              hasContent={isExpanded && !isListEmpty}
            />
          </SplitPane.Pane.Footer>
        </SplitPane.Pane>
      </InstanceSelectionProvider>
    );
  }
}

const WrappedListPanel = withData(withPoll(ListPanel));
WrappedListPanel.WrappedComponent = ListPanel;

export default WrappedListPanel;
