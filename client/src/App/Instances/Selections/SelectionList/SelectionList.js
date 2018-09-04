import React from 'react';
import PropTypes from 'prop-types';

import Selection from '../Selection/index.js';
import ContextualMessage from 'modules/components/ContextualMessage';

import {NO_SELECTIONS_MESSAGE} from './constants';
import {MESSAGES_TYPE} from 'modules/constants';

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
    const {
      selections,
      openSelection,
      onRetrySelection,
      onToggleSelection,
      onDeleteSelection
    } = this.props;
    return (
      <Styled.SelectionList>
        {selections.length === 10 && (
          <Styled.MessageWrapper>
            <ContextualMessage type={MESSAGES_TYPE.DROP_SELECTION} />
          </Styled.MessageWrapper>
        )}
        {selections.length ? (
          selections.map(selection => {
            const {selectionId, workflowInstances, totalCount} = selection;

            return (
              <Styled.Li key={selectionId}>
                <Selection
                  isOpen={openSelection === selectionId}
                  selectionId={selectionId}
                  instances={workflowInstances}
                  instanceCount={totalCount}
                  onRetry={() => onRetrySelection(openSelection)}
                  onToggle={() => onToggleSelection(selectionId)}
                  onDelete={() => onDeleteSelection(selectionId)}
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
