import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import {EXPAND_STATE, FILTER_SELECTION} from 'modules/constants';

import List from './List';
import ListFooter from './ListFooter';

import {fetchWorkflowInstances} from 'modules/api/instances';
import {parseFilterForRequest} from 'modules/utils/filter';

import {getInstancesWithActiveOperations} from './service';
import * as Styled from './styled';

const POLLING_WINDOW = 5000;

export default class ListView extends React.Component {
  static propTypes = {
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    filter: PropTypes.object.isRequired,
    filterCount: PropTypes.number.isRequired,
    instancesLoaded: PropTypes.bool,
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
      instancesWithActiveOperations: []
    };

    this.pollingTimer = null;
  }

  componentDidUpdate(prevProps, prevState) {
    const hasListChanged =
      !prevProps.instancesLoaded && this.props.instancesLoaded;

    const hasEntriesPerPageChanged =
      this.props.instancesLoaded &&
      this.state.entriesPerPage !== prevState.entriesPerPage;
    const isListExpanded = this.state.entriesPerPage > prevState.entriesPerPage;

    if (hasListChanged) {
      this.resetInstancesWithActiveOperations();
      this.checkForInstancesWithActiveOperations();
    }

    // this.props.instances does not change, so hasListChanged = false
    if (hasEntriesPerPageChanged) {
      // fetch the list again when expanding the list panel
      // https://app.camunda.com/jira/browse/OPE-395
      if (isListExpanded) {
        this.props.onWorkflowInstancesRefresh();
      }

      const activeIds = this.state.instancesWithActiveOperations.map(
        item => item.id
      );
      const idsInView = this.props.instances
        .slice(0, this.state.entriesPerPage)
        .map(item => item.id);

      const activeIdsInView = activeIds.filter(item =>
        idsInView.includes(item)
      );

      if (!activeIdsInView.length) {
        this.resetInstancesWithActiveOperations();
      }
    }

    if (
      prevState.instancesWithActiveOperations.length !==
      this.state.instancesWithActiveOperations.length
    ) {
      if (this.state.instancesWithActiveOperations.length > 0) {
        // first call of initializePolling
        this.pollingTimer === null && this.initializePolling();
      } else {
        // stop polling, no active operations
        this.clearPolling();
      }
    }
  }

  componentWillUnmount() {
    this.clearPolling();
  }

  resetInstancesWithActiveOperations = () => {
    this.setState({instancesWithActiveOperations: []});
  };

  checkForInstancesWithActiveOperations = () => {
    const instancesInView = this.props.instances.slice(
      0,
      this.state.entriesPerPage
    );
    const instancesWithActiveOperations = getInstancesWithActiveOperations(
      instancesInView
    );
    if (instancesWithActiveOperations.length > 0) {
      this.setState({
        instancesWithActiveOperations
      });
    }
  };

  initializePolling = () => {
    const shouldStart = this.state.instancesWithActiveOperations.length !== 0;
    if (shouldStart) {
      this.pollingTimer = setTimeout(
        this.detectInstancesChangesPoll,
        POLLING_WINDOW
      );
    }
  };

  clearPolling = () => {
    this.pollingTimer && clearTimeout(this.pollingTimer);
    this.pollingTimer = null;
  };

  detectInstancesChangesPoll = async () => {
    const ids = this.state.instancesWithActiveOperations.map(
      instance => instance.id
    );
    const instancesByIds = await this.fetchWorkflowInstancesByIds(ids);
    const instancesWithActiveOperations = getInstancesWithActiveOperations(
      instancesByIds
    );

    if (
      instancesWithActiveOperations.length !==
      this.state.instancesWithActiveOperations.length
    ) {
      this.props.onWorkflowInstancesRefresh();
    } else {
      this.initializePolling();
    }
  };

  fetchWorkflowInstancesByIds = async ids => {
    const instances = await fetchWorkflowInstances({
      queries: [
        parseFilterForRequest({
          ...FILTER_SELECTION.running,
          ...FILTER_SELECTION.finished,
          ids: ids.join(',')
        })
      ],
      firstResult: 0,
      maxResults: this.state.instancesWithActiveOperations.length
    });

    return instances.workflowInstances;
  };

  handleActionButtonClick = instance => {
    this.setState(prevState => {
      return {
        instancesWithActiveOperations: [
          ...prevState.instancesWithActiveOperations,
          instance
        ]
      };
    });
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
