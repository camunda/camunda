import React from 'react';
import PropTypes from 'prop-types';

import Selection from '../Selection/index.js';
import ContextualMessage from 'modules/components/ContextualMessage';

import {NO_SELECTIONS_MESSAGE} from './constants';

import * as Styled from './styled.js';

export default class SelectionList extends React.Component {
  static propTypes = {
    selections: PropTypes.array.isRequired,
    openSelection: PropTypes.number,
    onToggleSelection: PropTypes.func.isRequired,
    onDeleteSelection: PropTypes.func.isRequired,
    onRetrySelection: PropTypes.func.isRequired
  };

  render() {
    return (
      <Styled.SelectionList>
        {this.props.selections.length === 10 && (
          <Styled.MessageWrapper>
            <ContextualMessage type={'DROP_SELECTION'} />
          </Styled.MessageWrapper>
        )}
        {this.props.selections.length ? (
          this.props.selections.map(selection => {
            const {selectionId, workflowInstances, totalCount} = selection;

            return (
              <Styled.Li key={selectionId}>
                <Selection
                  isOpen={this.props.openSelection === selectionId}
                  selectionId={selectionId}
                  instances={workflowInstances}
                  instanceCount={totalCount}
                  onRetry={() =>
                    this.props.onRetrySelection(this.props.openSelection)
                  }
                  onToggle={() => this.props.onToggleSelection(selectionId)}
                  onDelete={() => this.props.onDeleteSelection(selectionId)}
                />
              </Styled.Li>
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
