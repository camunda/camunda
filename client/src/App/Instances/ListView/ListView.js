import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import {
  EXPAND_STATE,
  FILTER_SELECTION,
  DEFAULT_MAX_RESULTS
} from 'modules/constants';

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
    if (
      prevState.instancesWithActiveOperations.length !==
      this.state.instancesWithActiveOperations.length
    ) {
      if (this.state.instancesWithActiveOperations.length > 0) {
        // first call of initializePolling
        this.pollingTimer === null && this.initializePolling();
      } else {
        // stop polling as all operations have finished
        this.clearPolling();
      }
    }
  }

  componentWillUnmount() {
    this.clearPolling();
  }

  initializePolling = () => {
    const shouldStart = this.state.instancesWithActiveOperations.length !== 0;

    if (shouldStart) {
      this.pollingTimer = setTimeout(
        this.detectInstanceChangesPoll,
        POLLING_WINDOW
      );
    }
  };

  clearPolling = () => {
    this.pollingTimer && clearTimeout(this.pollingTimer);
    this.pollingTimer = null;
  };

  detectInstanceChangesPoll = async () => {
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
      this.setState({instancesWithActiveOperations}, () => {
        this.props.onWorkflowInstancesRefresh();
      });
    }

    this.initializePolling();
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

  handleOperationTrigger = instance => {
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
            onActionButtonClick={this.handleOperationTrigger}
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
