import React from 'react';
import PropTypes from 'prop-types';

import {fetchWorkflowInstanceBySelection} from 'modules/api/instances';
import {batchRetry} from 'modules/api/selections';
import Selection from '../Selection/index.js';

import {NO_SELECTIONS_MESSAGE} from './constants';
import * as Styled from './styled.js';

export default class SelectionList extends React.Component {
  static propTypes = {
    selections: PropTypes.array.isRequired,
    onChange: PropTypes.func.isRequired
  };
  state = {
    selectionsInstances: [],
    openSelection: null
  };

  componentDidMount = async () => {
    this.props.selections.map(async (selection, index) => {
      await this.getSelectionDetails(selection);
      this.setInstancesCount(this.state.selectionsInstances);
    });
  };

  componentDidUpdate = async prevProps => {
    if (this.props.selections !== prevProps.selections) {
      const selectionIds = this.getSelectionIds(this.state.selectionsInstances);

      this.props.selections.map(async (selection, index) => {
        if (!selectionIds.includes(selection.selectionId)) {
          await this.getSelectionDetails(selection);
          this.setInstancesCount(this.state.selectionsInstances);
        }
      });
    }
  };

  getSelectionIds = selections =>
    selections.map(selection => selection.selectionId);

  setInstancesCount = selections => {
    const selectionCount = selections.length;
    const instanceCount = selections
      .map(item => item.totalCount)
      .reduce((prev, next) => prev + next);

    this.props.onChange({instanceCount, selectionCount});
  };

  getSelectionDetails = async selection => {
    const {selectionId, ...payload} = selection;
    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);
    this.setState(prevState => ({
      selectionsInstances: [
        {...selection, ...instancesDetails},
        ...prevState.selectionsInstances
      ]
    }));
  };

  retrySelection = async evt => {
    evt && evt.stopPropagation();

    try {
      await batchRetry();
    } catch (e) {
      console.log(e);
    }
  };

  deleteSelection = async selectionID => {
    const {selectionsInstances} = this.state;
    const deletedInstance = selectionsInstances
      .map(({selectionId, totalCount}, index) => {
        console.log(
          'selectionId === selectionID && {selectionId, totalCount, index}',
          selectionId === selectionID && {selectionId, totalCount, index}
        );
        return selectionId === selectionID && {selectionId, totalCount, index};
      })
      .filter(instance => instance.selectionId)
      .shift();

    selectionsInstances.splice(deletedInstance.index, 1);

    await this.setState({
      selectionsInstances
    });

    this.props.onChange(this.state.selectionsInstances);
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
            const {selectionId, workflowInstances, totalCount} = selection;

            return (
              <Styled.SelectionWrapper key={selectionId}>
                <Selection
                  isOpen={this.state.openSelection === selectionId}
                  selectionId={selectionId}
                  instances={workflowInstances}
                  count={totalCount}
                  onClick={() => this.toggleSelection(selectionId)}
                  onRetry={evt => this.retrySelection(evt)}
                  onDelete={() => this.deleteSelection(selectionId)}
                />
              </Styled.SelectionWrapper>
            );
          })
        ) : (
          <Styled.NoSelectionWrapper>
            {NO_SELECTIONS_MESSAGE}
          </Styled.NoSelectionWrapper>
        )}
      </Styled.SelectionList>
    );
  }
}
