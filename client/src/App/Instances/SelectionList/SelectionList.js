import React from 'react';
import PropTypes from 'prop-types';

import {batchRetry} from 'modules/api/selections';
import Selection from '../Selection/index.js';

import {NO_SELECTIONS_MESSAGE} from './constants';
import * as Styled from './styled.js';

export default class SelectionList extends React.Component {
  static propTypes = {
    openSelection: PropTypes.number,
    selections: PropTypes.array.isRequired,
    onDelete: PropTypes.func.isRequired,
    onToggle: PropTypes.func.isRequired
  };
  state = {
    openSelection: null
  };

  retrySelection = async evt => {
    evt && evt.stopPropagation();

    try {
      await batchRetry();
    } catch (e) {
      console.log(e);
    }
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
                  onRetry={evt => this.retrySelection(evt)}
                  onToggle={() => this.props.onToggle(selectionId)}
                  onDelete={() => this.props.onDelete(selectionId)}
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
