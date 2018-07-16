import React from 'react';

import * as Styled from './styled.js';

import {fetchWorkflowInstanceBySelection} from 'modules/api/instances';

import Selection from '../Selection';

export default class SelectionList extends React.Component {
  state = {selectionsInstances: [], newSelectionIndex: 0, openSelection: null};

  componentDidMount = async () => {
    this.props.selections.map(async selection => {
      const selectionData = await fetchWorkflowInstanceBySelection(selection);
      this.setState(prevState => ({
        selectionsInstances: [
          ...prevState.selectionsInstances,
          {...selectionData, id: this.state.newSelectionIndex}
        ],
        newSelectionIndex: this.state.newSelectionIndex + 1
      }));
    });
  };

  retySelection = async selectionObject => {
    //TODO: handle request
    // try {
    //   await batchRety(selectionObject);
    // } catch (e) {
    //   console.log(e);
    // }
    console.log('selection instances are retried');
  };

  deleteSelection = selectionID => {
    const selectionIndex = this.state.selectionsInstances
      .map(instance => instance.id)
      .indexOf(selectionID);

    this.state.selectionsInstances.splice(selectionIndex, 1);

    this.setState({
      selectionsInstances: this.state.selectionsInstances
    });
  };

  toggleSelection = selectionID => {
    this.setState({
      openSelection:
        selectionID !== this.state.openSelection ? selectionID : null
    });
  };

  render() {
    return (
      <Styled.SelectionList>
        {this.state.selectionsInstances.map((selection, index) => {
          const {id, workfowInstances, totalCount} = selection;
          return (
            <Selection
              isOpen={this.state.openSelection === id}
              key={id}
              selectionId={id}
              instances={workfowInstances}
              count={totalCount}
              onClick={() => this.toggleSelection(id)}
              onRetry={() => this.retySelection(selection)}
              onDelete={() => this.deleteSelection(id)}
            />
          );
        })}
      </Styled.SelectionList>
    );
  }
}
