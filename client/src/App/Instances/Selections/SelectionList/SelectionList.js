import React from 'react';
import PropTypes from 'prop-types';

import Selection from 'modules/components/Selection';

import {NO_SELECTIONS_MESSAGE} from './constants';
import {MESSAGES_TYPE} from 'modules/constants';

import * as Styled from './styled.js';

export default class SelectionList extends React.Component {
  static propTypes = {
    selections: PropTypes.array.isRequired,
    openSelection: PropTypes.number,
    onToggleSelection: PropTypes.func.isRequired,
    onDeleteSelection: PropTypes.func.isRequired,
    onRetrySelection: PropTypes.func.isRequired,
    onCancelSelection: PropTypes.func.isRequired
  };

  render() {
    const {
      selections,
      openSelection,
      onRetrySelection,
      onCancelSelection,
      onToggleSelection,
      onDeleteSelection
    } = this.props;
    return (
      <React.Fragment>
        {selections.length === 10 && (
          <Styled.MessageWrapper>
            <Styled.SelectionMessage type={MESSAGES_TYPE.DROP_SELECTION} />
          </Styled.MessageWrapper>
        )}

        <Styled.Ul>
          {selections.length ? (
            selections.map(selection => {
              const {selectionId, instancesMap, totalCount} = selection;

              return (
                <Styled.Li key={selectionId}>
                  <Selection
                    isOpen={openSelection === selectionId}
                    selectionId={selectionId}
                    instances={instancesMap}
                    instanceCount={totalCount}
                    onRetry={() => onRetrySelection(openSelection)}
                    onCancel={() => onCancelSelection(openSelection)}
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
        </Styled.Ul>
      </React.Fragment>
    );
  }
}
