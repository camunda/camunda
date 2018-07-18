import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

import {fetchWorkflowInstanceBySelection} from 'modules/api/instances';
import {batchRetry} from 'modules/api/selections';
import Selection from '../Selection';

export default class SelectionList extends React.Component {
  static propTypes = {
    selections: PropTypes.array.isRequired
  };
  state = {selectionsInstances: [], newSelectionIndex: 0, openSelection: null};
  componentDidMount = async () => {
    //TODO: replace loop with this.props.selections.map
    for (let index = 0; index < 10; index++) {
      const selectionIndex = this.state.newSelectionIndex;
      const selectionData = await fetchWorkflowInstanceBySelection();
      this.setState(prevState => ({
        selectionsInstances: [
          {...selectionData, id: selectionIndex},
          ...prevState.selectionsInstances
        ],
        newSelectionIndex: selectionIndex + 1
      }));
    }
  };

  retrySelection = async evt => {
    evt && evt.stopPropagation();

    try {
      await batchRetry();
    } catch (e) {
      console.log(e);
    }
  };

  deleteSelection = selectionID => {
    const {selectionsInstances} = this.state;
    const {selectionIndex} = selectionsInstances
      .map(instance => instance.id)
      .indexOf(selectionID);

    selectionsInstances.splice(selectionIndex, 1);

    this.setState({
      selectionsInstances
    });
  };

  toggleSelection = selectionID => {
    this.setState({
      openSelection:
        selectionID !== this.state.openSelection ? selectionID : null
    });
  };

  render() {
    const {selectionsInstances} = this.state;
    return (
      <Styled.SelectionList>
        {selectionsInstances.length ? (
          selectionsInstances.map(selection => {
            const {id, workfowInstances, totalCount} = selection;
            return (
              <Styled.SelectionWrapper key={id}>
                <Selection
                  isOpen={this.state.openSelection === id}
                  selectionId={id}
                  instances={workfowInstances}
                  count={totalCount}
                  onClick={() => this.toggleSelection(id)}
                  onRetry={evt => this.retrySelection(evt)}
                  onDelete={() => this.deleteSelection(id)}
                />
              </Styled.SelectionWrapper>
            );
          })
        ) : (
          <Styled.NoSelectionWrapper>
            To create a new Selection, select some instances from the list and
            click ”Create new Selection”.
          </Styled.NoSelectionWrapper>
        )}
      </Styled.SelectionList>
    );
  }
}
