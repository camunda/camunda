/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {withData} from 'modules/DataManager';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

// Creates a context for polling for updates on instances with active operations
const InstancesPollContext = React.createContext();
const InstancesPollConsumer = InstancesPollContext.Consumer;

function getCommonItems(list_1 = [], list_2 = []) {
  return list_1.length < list_2.length
    ? list_1.filter(item => list_2.includes(item))
    : list_2.filter(item => list_1.includes(item));
}

class InstancesPollProviderComp extends React.Component {
  static propTypes = {
    dataManager: PropTypes.object,
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    visibleIdsInSelections: PropTypes.array,
    filter: PropTypes.object
  };

  constructor(props) {
    super();
    this.state = {
      active: new Set([]),
      complete: new Set([])
    };
    this.subscriptions = {
      LOAD_SELECTION_INSTANCES: ({response, state}) => {
        if (state === LOADING_STATE.LOADED) {
          const idsByOperation = {
            active: new Set([]),
            complete: new Set([]),
            completedInstances: []
          };
          response.workflowInstances.forEach(item => {
            item.hasActiveOperation
              ? idsByOperation.active.add(item.id)
              : idsByOperation.complete.add(item.id);

            if (!item.hasActiveOperation) {
              idsByOperation.completedInstances.push(item);
            }
          });
          this.setState(idsByOperation);
        }
      }
    };
  }

  componentDidMount() {
    this.props.dataManager.subscribe(this.subscriptions);
  }

  componentDidUpdate(prevProps, prevState) {
    const {dataManager} = this.props;
    const {active, complete, completedInstances} = this.state;

    const hasActiveOperation = Boolean(active.size);
    const hasCompletedOperations = Boolean(complete.size);

    if (hasActiveOperation) {
      dataManager.poll.start(() =>
        dataManager.getWorkflowInstancesByIds(
          [...active],
          SUBSCRIPTION_TOPIC.LOAD_SELECTION_INSTANCES
        )
      );
    }

    if (hasCompletedOperations) {
      this.triggerDataUpdates(completedInstances);
      this.setState({complete: new Set([]), completedInstances: []});
    }
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  triggerDataUpdates(completedInstances) {
    const {
      dataManager,
      filter: {workflow, version}
    } = this.props;

    const completedIdsInSelections = getCommonItems([...this.state.complete]);

    let updateParams = {
      endpoints: [
        {name: SUBSCRIPTION_TOPIC.LOAD_LIST_INSTANCES},
        {name: SUBSCRIPTION_TOPIC.LOAD_CORE_STATS}
      ],
      topic: SUBSCRIPTION_TOPIC.REFRESH_AFTER_OPERATION
    };

    if (workflow && version && version !== 'all') {
      updateParams.endpoints = [
        ...updateParams.endpoints,
        SUBSCRIPTION_TOPIC.LOAD_STATE_STATISTICS
      ];
    }

    if (Boolean(completedIdsInSelections.length)) {
      updateParams.staticData = {
        completedInstances
      };
    }

    dataManager.update(updateParams);
  }

  addIds = ids =>
    this.setState(prevState => {
      return {active: new Set([...prevState.active, ...ids])};
    });

  /**
   * called only from listView when removing instance with active op. from view
   * - by collapsing
   * - by changing the view
   */
  removeIds = removeIds => {
    // update the state removing only the ids that are in the List and not in a selection
    // we want to poll for remaining ids in selections, even if they are hidden in ListView
    const remaningIds = [...this.state.active].filter(
      item =>
        removeIds.includes(item) &&
        this.props.visibleIdsInSelections.includes(item)
    );

    Boolean(remaningIds.length) &&
      this.setState({active: new Set(remaningIds)});
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

  WithPoll.propTypes = {
    forwardedRef: PropTypes.oneOfType([
      PropTypes.func,
      PropTypes.shape({current: PropTypes.instanceOf(Element)})
    ])
  };

  const WithPollWithRef = React.forwardRef((props, ref) => {
    return <WithPoll {...props} forwardedRef={ref} />;
  });

  WithPollWithRef.WrappedComponent = Component;
  const name = Component.displayName || Component.name;
  WithPollWithRef.displayName = `WithPoll(${name})`;
  return WithPollWithRef;
};

const InstancesPollProvider = withData(InstancesPollProviderComp);
InstancesPollProvider.WrappedComponent = InstancesPollProviderComp;

export {InstancesPollConsumer, InstancesPollProvider, withPoll};
