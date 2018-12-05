import React from 'react';
import PropTypes from 'prop-types';

import {
  serializeInstancesMaps,
  deserializeInstancesMaps
} from 'modules/utils/selection/selection';
import {
  fetchWorkflowInstanceBySelection,
  fetchWorkflowInstances
} from 'modules/api/instances';
import {getSelectionById} from 'modules/utils/selection';
import withSharedState from 'modules/components/withSharedState';

import {createMapOfInstances, getPayloadtoFetchInstancesById} from './service';

const SelectionContext = React.createContext();

export const SelectionConsumer = SelectionContext.Consumer;

class BasicSelectionProvider extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    getSelectionPayload: PropTypes.func.isRequired,
    getStateLocally: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired
  };

  constructor(props) {
    super(props);

    // read data from local storage
    const {
      instancesInSelectionsCount,
      rollingSelectionIndex,
      selectionCount,
      selections
    } = props.getStateLocally();

    this.state = {
      instancesInSelectionsCount: instancesInSelectionsCount || 0,
      openSelection: null,
      rollingSelectionIndex: rollingSelectionIndex || 0,
      selection: {all: false, ids: [], excludeIds: []},
      selectionCount: selectionCount || 0,
      selections: deserializeInstancesMaps(selections) || [],
      IdsOfInstancesInSelections: []
    };
  }

  componentDidMount() {
    if (this.state.selectionCount) {
      this.getIdsOfInstancesInSelections();
      this.updateInstancesInSelections();
    }
  }

  getIdsOfInstancesInSelections() {
    let ids = new Set();
    this.state.selections.map(
      selection =>
        (ids = new Set([...ids, ...[...selection.instancesMap.keys()]]))
    );
    this.setState({IdsOfInstancesInSelections: ids});
  }

  handleAddNewSelection = async () => {
    const payload = this.props.getSelectionPayload({
      selectionState: this.state
    });
    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);
    const {workflowInstances, ...rest} = instancesDetails;
    const instancesMap = createMapOfInstances(workflowInstances);
    const selection = {
      instancesMap,
      ...payload,
      ...rest
    };
    const {
      rollingSelectionIndex,
      instancesInSelectionsCount,
      selectionCount,
      selections
    } = this.state;

    const currentSelectionIndex = rollingSelectionIndex + 1;
    const newCount = instancesInSelectionsCount + selection.totalCount;

    // Add Id for each selection
    this.setState(
      {
        selections: [
          {
            selectionId: currentSelectionIndex,
            ...selection
          },
          ...selections
        ],
        rollingSelectionIndex: currentSelectionIndex,
        instancesInSelectionsCount: newCount,
        selectionCount: selectionCount + 1,
        openSelection: currentSelectionIndex,
        selection: {all: false, ids: [], excludeIds: []}
      },
      () => {
        const {
          selections,
          rollingSelectionIndex,
          instancesInSelectionsCount,
          selectionCount
        } = this.state;

        this.props.storeStateLocally({
          instancesInSelectionsCount,
          selections: serializeInstancesMaps(selections),
          rollingSelectionIndex,
          selectionCount
        });
      }
    );
  };

  handleAddToSelectionById = async selectionId => {
    const payload = this.props.getSelectionPayload({
      selectionState: this.state,
      selectionId
    });
    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);
    const {workflowInstances, ...rest} = instancesDetails;
    const newInstancesMap = createMapOfInstances(workflowInstances);

    const {selections, instancesInSelectionsCount} = this.state;

    const {index: selectionIndex} = getSelectionById(selections, selectionId);

    const {totalCount} = selections[selectionIndex];

    const newSelection = {
      ...selections[selectionIndex],
      instancesMap: newInstancesMap,
      ...payload,
      ...rest
    };

    selections[selectionIndex] = newSelection;

    const newCount =
      instancesInSelectionsCount - totalCount + newSelection.totalCount;

    this.setState(
      {
        selections,
        instancesInSelectionsCount: newCount,
        selection: {all: false, ids: [], excludeIds: []},
        openSelection: selectionId
      },
      () => {
        const {instancesInSelectionsCount, selections} = this.state;
        this.props.storeStateLocally({
          instancesInSelectionsCount,
          selections: serializeInstancesMaps(selections)
        });
      }
    );
  };

  handleAddToOpenSelection = () => {
    this.handleAddToSelectionById(this.state.openSelection);
  };

  handleToggleSelection = selectionId => {
    this.setState({
      openSelection:
        selectionId !== this.state.openSelection ? selectionId : null
    });
  };

  handleDeleteSelection = async selectionId => {
    const {selections, instancesInSelectionsCount, selectionCount} = this.state;

    const selectionToRemove = getSelectionById(selections, selectionId);
    // remove the selection
    selections.splice(selectionToRemove.index, 1);

    this.setState(
      {
        selections,
        instancesInSelectionsCount:
          instancesInSelectionsCount - selectionToRemove.totalCount,
        selectionCount: selectionCount - 1 || 0
      },
      () => {
        this.props.storeStateLocally({
          selections: serializeInstancesMaps(selections),
          instancesInSelectionsCount: this.state.instancesInSelectionsCount,
          selectionCount: this.state.selectionCount
        });
      }
    );
  };

  updateSelection = selection => {
    this.setState({selection});
  };

  resetSelections = () => {
    this.setState({selection: {all: false, ids: [], excludeIds: []}});
  };

  async updateInstancesInSelections() {
    const workflowInstances = await this.fetchInstancesInSelection();
    const updatedInstanceMap = createMapOfInstances(workflowInstances);
    this.updateSelections(updatedInstanceMap);
  }

  async fetchInstancesInSelection() {
    const {IdsOfInstancesInSelections: IdsOfInstances} = this.state;
    const payload = getPayloadtoFetchInstancesById(IdsOfInstances);
    const options = {
      firstResult: 0,
      maxResults: IdsOfInstances.size,
      ...payload
    };
    const {workflowInstances} = await fetchWorkflowInstances(options);
    return workflowInstances;
  }

  render() {
    const contextValue = {
      ...this.state,
      onUpdateSelection: this.updateSelection,
      onAddNewSelection: this.handleAddNewSelection,
      onAddToSelectionById: this.handleAddToSelectionById,
      onAddToOpenSelection: this.handleAddToOpenSelection,
      onToggleSelection: this.handleToggleSelection,
      onDeleteSelection: this.handleDeleteSelection
    };

    return (
      <SelectionContext.Provider value={contextValue}>
        {this.props.children}
      </SelectionContext.Provider>
    );
  }
}

export const withSelection = Component => {
  function WithSelection(props) {
    return (
      <SelectionConsumer>
        {contextValue => <Component {...props} {...contextValue} />}
      </SelectionConsumer>
    );
  }

  WithSelection.WrappedComponent = Component;

  WithSelection.displayName = `WithSelection(${Component.displayName ||
    Component.name ||
    'Component'})`;

  return WithSelection;
};

export const SelectionProvider = withSharedState(BasicSelectionProvider);
