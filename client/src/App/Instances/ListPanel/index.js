/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {EXPAND_STATE} from 'modules/constants';

import {getInstancesWithActiveOperations} from 'modules/utils/instance';

import {withPoll} from 'modules/contexts/InstancesPollContext';
import {withData} from 'modules/DataManager';

import SplitPane from 'modules/components/SplitPane';
import {filters} from 'modules/stores/filters';
import {observer} from 'mobx-react';

import List from './List';
import ListFooter from './ListFooter';
import * as Styled from './styled';

const ListPanel = observer(
  class ListPanel extends React.Component {
    static propTypes = {
      expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
      instances: PropTypes.array.isRequired,
      isInitialLoadComplete: PropTypes.bool,
      isLoading: PropTypes.bool,
      polling: PropTypes.shape({
        active: PropTypes.object,
        removeIds: PropTypes.func,
        addIds: PropTypes.func,
      }),
    };

    componentDidUpdate(prevProps, prevState) {
      // const {dataManager} = this.props;

      const hasListChanged = prevProps.isLoading && !this.props.isLoading;

      // TODO: this code block does not work right now because of a typo
      // (this.state.instancesLoad does not exist, it should be this.props.instancesLoaded instead)
      // fixing it causes another bug. so will handle it later

      // const hasEntriesPerPageChanged =
      //   this.state.instancesLoaded &&
      //   this.state.entriesPerPage !== prevState.entriesPerPage;
      // const isListExpanded = this.state.entriesPerPage > prevState.entriesPerPage;
      // https://jira.camunda.com/browse/OPE-1080

      if (hasListChanged) {
        if (prevProps.instances.length) {
          const prevActiveInstances = prevProps.instances
            .slice(0, filters.state.prevEntriesPerPage)
            .filter((item) => this.props.polling.active.has(item.id));

          // remove instances with active ops.  from previous page from polling
          Boolean(prevActiveInstances.length) &&
            this.props.polling.removeIds(
              prevActiveInstances.map((instance) => instance.id)
            );
        }

        this.checkForInstancesWithActiveOperations();
      }

      // TODO: Commented for the same reason above.

      // this.props.instances does not change, so hasListChanged = false
      // if (hasEntriesPerPageChanged) {
      //   if (isListExpanded) {
      //     dataManager.update({
      //       endpoints: [SUBSCRIPTION_TOPIC.LOAD_LIST_INSTANCES],
      //       topic: SUBSCRIPTION_TOPIC.REFRESH_AFTER_OPERATION,
      //     });
      //   } else {
      //     // list is collapsed
      //     const activeIds = this.props.polling.active;

      //     const activeInstancesNotInView = this.props.instances
      //       .slice(this.state.entriesPerPage) // get hidden ids from collapse
      //       .filter((item) => activeIds.has(item.id)) // get only active ids
      //       .map((item) => item.id);
      //     if (Boolean(activeInstancesNotInView.length)) {
      //       this.props.polling.removeIds(activeInstancesNotInView);
      //     }
      //   }
      // }
      // https://jira.camunda.com/browse/OPE-1080
    }

    checkForInstancesWithActiveOperations = () => {
      const instancesInView = this.props.instances.slice(
        0,
        filters.state.entriesPerPage
      );
      const instancesWithActiveOperations = getInstancesWithActiveOperations(
        instancesInView
      );
      if (instancesWithActiveOperations.length > 0) {
        this.props.polling.addIds(
          instancesWithActiveOperations.map((instance) => instance.id)
        );
      }
    };

    handleOperationButtonClick = (instance) => {
      this.props.polling.addIds([instance.id]);
    };

    getEmptyListMessage = () => {
      const {
        filter: {active, incidents, completed, canceled},
      } = filters.state;

      let msg = 'There are no instances matching this filter set.';

      if (!active && !incidents && !completed && !canceled) {
        msg += '\n To see some results, select at least one instance state.';
      }

      return msg;
    };

    renderContent(isListEmpty) {
      if (!isListEmpty) {
        return <List.Body />;
      } else if (!this.props.isInitialLoadComplete) {
        return <List.Skeleton />;
      } else {
        return <List.Message message={this.getEmptyListMessage()} />;
      }
    }

    renderSpinner() {
      return (
        this.props.isLoading &&
        this.props.isInitialLoadComplete &&
        (() => <Styled.Spinner />)
      );
    }
    render() {
      const isListEmpty = this.props.instances.length === 0;
      const isExpanded = this.props.expandState !== EXPAND_STATE.COLLAPSED;

      return (
        <SplitPane.Pane {...this.props} hasShiftableControls>
          <SplitPane.Pane.Header>Instances</SplitPane.Pane.Header>

          <Styled.PaneBody>
            <List
              data={this.props.instances}
              expandState={this.props.expandState}
              isDataLoaded={!this.props.isLoading}
              onOperationButtonClick={this.handleOperationButtonClick}
              Overlay={this.renderSpinner()}
            >
              <List.Header />
              {this.renderContent(isListEmpty)}
            </List>
          </Styled.PaneBody>
          <SplitPane.Pane.Footer>
            <ListFooter hasContent={isExpanded && !isListEmpty} />
          </SplitPane.Pane.Footer>
        </SplitPane.Pane>
      );
    }
  }
);

const WrappedListPanel = withData(withPoll(ListPanel));
WrappedListPanel.WrappedComponent = ListPanel;

export default WrappedListPanel;
