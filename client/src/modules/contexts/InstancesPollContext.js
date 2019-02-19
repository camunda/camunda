import React from 'react';
import PropTypes from 'prop-types';
import {uniq} from 'lodash';

import {fetchWorkflowInstancesByIds} from 'modules/api/instances';

const POLLING_WINDOW = 5000;

// Creates a context for polling for updates on instances with active operations
const InstancesPollContext = React.createContext();
const InstancesPollConsumer = InstancesPollContext.Consumer;

function getCommonItems(list_1 = [], list_2 = []) {
  return list_1.length < list_2.length
    ? list_1.filter(item => list_2.includes(item))
    : list_2.filter(item => list_1.includes(item));
}

class InstancesPollProvider extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    onWorkflowInstancesRefresh: PropTypes.func,
    onSelectionsRefresh: PropTypes.func,
    visibleIdsInListView: PropTypes.array,
    visibleIdsInSelections: PropTypes.array
  };

  constructor(props) {
    super();
    this.state = {ids: []};
    this.pollingTimer = null;
  }

  componentDidUpdate(prevProps, prevState) {
    if (prevState.ids.length !== this.state.ids.length) {
      Boolean(this.state.ids.length) &&
        this.pollingTimer === null &&
        this.initializePolling();
    }
  }

  componentWillUnmount() {
    this.clearPolling();
  }

  initializePolling = () => {
    if (Boolean(this.state.ids.length)) {
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

  resetPoll = () => {
    this.clearPolling();
    this.setState({ids: []});
  };

  fetchWorkflowInstancesByIds = async ids => {
    const instances = await fetchWorkflowInstancesByIds(ids);

    return instances.workflowInstances;
  };

  handlePoll = () => {
    const idsInSelections = getCommonItems(
      this.state.ids,
      this.props.visibleIdsInSelections
    );

    /* only update Selections if we have active operations present there */
    Boolean(idsInSelections.length) && this.props.onSelectionsRefresh();

    /* always update Instances to reflect new count and statistics */
    this.props.onWorkflowInstancesRefresh();
  };

  detectInstancesChangesPoll = async () => {
    const ids = this.state.ids;
    const instancesByIds = await this.fetchWorkflowInstancesByIds(ids);
    const idsByOperation = {active: [], complete: []};
    instancesByIds.forEach(item => {
      item.hasActiveOperation
        ? idsByOperation.active.push(item.id)
        : idsByOperation.complete.push(item.id);
    });
    const hasActiveOperation = idsByOperation.active.length !== 0;
    const hasCompletedOperations = idsByOperation.complete.length !== 0;

    if (hasCompletedOperations) {
      this.handlePoll();

      if (hasActiveOperation) {
        this.setState(
          {
            ids: idsByOperation.active
          },
          () => {
            this.initializePolling();
          }
        );
      } else {
        this.resetPoll();
      }
    } else {
      hasActiveOperation && this.initializePolling();
    }
  };

  addIds = ids => {
    this.setState(prevState => {
      const newIds = uniq([...ids, ...prevState.ids]);
      return {ids: newIds};
    });
  };
  /**
   * called only from listView when removing instance with active op. from view
   * - by collapsing
   * - by changing the view
   */
  removeIds = removeIds => {
    // update the state removing only the ids that are in the List and not in a selection
    // we want to poll for remaining ids in selections, even if they are hidden in ListView
    const remaningIds = this.state.ids.filter(
      item =>
        removeIds.includes(item) &&
        this.props.visibleIdsInSelections.includes(item)
    );

    Boolean(remaningIds.length)
      ? this.setState({ids: remaningIds})
      : this.resetPoll();
  };

  render() {
    const contextValue = {
      ...this.state,
      addIds: this.addIds,
      removeIds: this.removeIds
    };

    return (
      <InstancesPollContext.Provider value={contextValue}>
        {this.props.children}
      </InstancesPollContext.Provider>
    );
  }
}

const withPoll = Component => {
  class WithPoll extends React.Component {
    render() {
      const {forwardedRef, ...rest} = this.props;
      return (
        <InstancesPollConsumer>
          {polling => (
            <Component ref={forwardedRef} {...rest} polling={polling} />
          )}
        </InstancesPollConsumer>
      );
    }
  }

  const WithPollWithRef = React.forwardRef((props, ref) => {
    return <WithPoll {...props} forwardedRef={ref} />;
  });

  WithPollWithRef.WrappedComponent = Component;
  const name = Component.displayName || Component.name;
  WithPollWithRef.displayName = `WithPoll(${name})`;
  return WithPollWithRef;
};

export {InstancesPollConsumer, InstancesPollProvider, withPoll};
