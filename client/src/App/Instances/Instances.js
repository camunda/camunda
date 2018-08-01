import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import Content from 'modules/components/Content';
import Panel from 'modules/components/Panel';
import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import {DIRECTION, DEFAULT_FILTER} from 'modules/constants';
import {fetchWorkflowInstancesCount} from 'modules/api/instances';
import {
  parseFilterForRequest,
  getFilterQueryString
} from 'modules/utils/filter';

import {BADGE_TYPE} from 'modules/constants';

import Header from '../Header';
import ListView from './ListView';
import SelectionList from './SelectionList';
import {
  parseQueryString,
  createNewSelectionFragment,
  getParentFilter
} from './service';
import Filters from './Filters';
import * as Styled from './styled.js';

class Instances extends Component {
  static propTypes = {
    getStateLocally: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired,
    location: PropTypes.object.isRequired,
    history: PropTypes.object.isRequired
  };

  constructor(props) {
    super(props);
    const {filterCount, selections} = props.getStateLocally();

    this.state = {
      errorMessage: null,
      filter: {},
      filterCount: filterCount || 0,
      selection: createNewSelectionFragment(),
      selections: selections || [],
      instancesInSelections: 5000,
      selectionCount: 10,
      rollingSelectionIndex: 0,
      currentSelection: null,
      workflow: null,
      activityIds: []
    };
  }

  async componentDidMount() {
    this.setFilterFromUrl();
  }

  componentDidUpdate(prevProps, prevState) {
    if (prevProps.location.search !== this.props.location.search) {
      this.setFilterFromUrl();
    }
  }

  addNewSelection = async () => {
    const {
      rollingSelectionIndex: currentSelectionIndex,
      selection
    } = this.state;

    // push new Selection to selections array, replace Sets with arrays.
    await this.setState(prevState => ({
      selections: [
        {
          selectionId: currentSelectionIndex,
          queries: [
            {
              ...this.state.filter,
              ...getParentFilter(this.state.filter),
              ...selection,
              ids: [...(selection.ids || [])],
              excludeIds: [...(selection.excludeIds || [])]
            }
          ]
        },
        ...prevState.selections
      ],
      rollingSelectionIndex: currentSelectionIndex + 1
    }));
    this.props.storeStateLocally({selections: this.state.selections});
  };

  updateSelection = change => {
    this.setState({
      selection: {...change}
    });
  };

  handleAddToSelection = () => {
    this.addNewSelection();
    // addToSelection()
  };

  setFilterFromUrl = () => {
    let {filter} = parseQueryString(this.props.location.search);

    // filter from URL was missing or invalid
    if (!filter) {
      // set default filter selection
      filter = DEFAULT_FILTER;
      this.setFilterInURL(filter);
    }

    // filter is valid
    this.setState({filter}, () => {
      this.handleFilterCount();
    });
  };

  handleFilterCount = async () => {
    const filterCount = await fetchWorkflowInstancesCount(
      parseFilterForRequest(this.state.filter)
    );

    this.setState({
      filterCount
    });

    // save filterCount to localStorage
    this.props.storeStateLocally({filterCount});
  };

  handleFilterChange = async newFilter => {
    const filter = {...this.state.filter, ...newFilter};
    this.setFilterInURL(filter);

    // write current filter selection to local storage
    this.props.storeStateLocally({filter: filter});
  };

  handleWorkflowChange = workflow => {
    this.setState({workflow});
  };

  setFilterInURL = filter => {
    this.props.history.push({
      pathname: this.props.location.pathname,
      search: getFilterQueryString(filter)
    });
  };

  resetFilter = () => {
    this.setFilterInURL(DEFAULT_FILTER);

    // reset filter in local storage
    this.props.storeStateLocally({filter: DEFAULT_FILTER});
  };

  adjustSelectionCounter = ({instanceCount, selectionCount}) => {
    // this.setState({
    //   instancesInSelections: instanceCount,
    //   selectionCount: selectionCount
    // });
  };

  handleFlowNodesDetailsReady = nodes => {
    const activityIds = [];
    let node;

    for (node in nodes) {
      if (nodes[node].type === 'TASK') {
        activityIds.push({
          value: node,
          label: nodes[node].name || 'Unnamed task'
        });
      }
    }

    this.setState({
      activityIds
    });
  };

  render() {
    const {running, incidents: incidentsCount} = this.props.getStateLocally();

    return (
      <Fragment>
        <Header
          active="instances"
          instances={running}
          filters={this.state.filterCount}
          selections={0} // needs a backend call because selections are complex
          incidents={incidentsCount}
        />
        <Content>
          <Styled.Instances>
            <Styled.Filters>
              <Filters
                filter={this.state.filter}
                activityIds={this.state.activityIds}
                resetFilter={this.resetFilter}
                onFilterChange={this.handleFilterChange}
                onWorkflowVersionChange={this.handleWorkflowChange}
              />
            </Styled.Filters>

            <Styled.Center>
              <SplitPane.Pane isRounded>
                <SplitPane.Pane.Header isRounded>
                  {this.state.workflow
                    ? this.state.workflow.name || this.state.workflow.id
                    : 'Workflow'}
                </SplitPane.Pane.Header>
                <SplitPane.Pane.Body>
                  {this.state.workflow && (
                    <Diagram
                      workflowId={this.state.workflow.id}
                      onFlowNodesDetailsReady={this.handleFlowNodesDetailsReady}
                    />
                  )}
                </SplitPane.Pane.Body>
              </SplitPane.Pane>

              <ListView
                instancesInFilter={this.state.filterCount}
                filters={{
                  errorMessage: this.state.errorMessage
                }}
                onSelectionUpdate={change => {
                  this.updateSelection(change);
                }}
                selection={this.state.selection}
                filter={this.state.filter}
                onAddToSelection={this.handleAddToSelection}
                errorMessage={this.state.errorMessage}
              />
            </Styled.Center>
            <Styled.Selections>
              <Panel isRounded>
                <Styled.SelectionHeader isRounded>
                  <span>Selections</span>
                  <Styled.Badge
                    type={BADGE_TYPE.COMBOSELECTION}
                    badgeContent={this.state.instancesInSelections}
                    circleContent={this.state.selectionCount}
                  />
                </Styled.SelectionHeader>
                <Panel.Body>
                  <SelectionList
                    selections={this.state.selections}
                    onChange={this.adjustSelectionCounter}
                  />
                </Panel.Body>
                <Styled.RightExpandButton
                  direction={DIRECTION.RIGHT}
                  isExpanded={true}
                />
                <Panel.Footer />
              </Panel>
            </Styled.Selections>
          </Styled.Instances>
        </Content>
      </Fragment>
    );
  }
}

const WrappedInstances = withSharedState(Instances);
WrappedInstances.WrappedComponent = Instances;

export default WrappedInstances;
