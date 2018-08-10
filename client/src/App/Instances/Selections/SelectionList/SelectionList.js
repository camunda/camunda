import React from 'react';
import PropTypes from 'prop-types';

import Selection from '../Selection/index.js';

import {NO_SELECTIONS_MESSAGE} from './constants';
import * as Styled from './styled.js';

export default class SelectionList extends React.Component {
  static propTypes = {
    selections: PropTypes.array.isRequired,
    openSelection: PropTypes.number,
    toggleSelection: PropTypes.func.isRequired,
    deleteSelection: PropTypes.func.isRequired,
    retrySelection: PropTypes.func.isRequired
  };

  render() {
    return (
      <Styled.SelectionList>
        {this.props.selections.length ? (
          this.props.selections.map(selection => {
            const {selectionId, workflowInstances, totalCount} = selection;

            return (
              <Styled.SelectionWrapper key={selectionId}>
                <Selection
                  isOpen={this.props.openSelection === selectionId}
                  selectionId={selectionId}
                  instances={workflowInstances}
                  instanceCount={totalCount}
                  onRetry={evt => this.props.retrySelection(evt)}
                  onToggle={() => this.props.toggleSelection(selectionId)}
                  onDelete={() => this.props.deleteSelection(selectionId)}
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
