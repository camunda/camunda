import React from 'react';
import PropTypes from 'prop-types';
import {CSSTransition, TransitionGroup} from 'react-transition-group';

import Selection from 'modules/components/Selection';
import {withSelection} from 'modules/contexts/SelectionContext';
import {getSelectionById} from 'modules/utils/selection';
import {applyOperation} from 'modules/api/instances';

import {NO_SELECTIONS_MESSAGE} from './constants';
import {MESSAGES_TYPE, OPERATION_TYPE} from 'modules/constants';

import * as Styled from './styled.js';

class SelectionList extends React.Component {
  static propTypes = {
    selections: PropTypes.array.isRequired,
    openSelection: PropTypes.number,
    onToggleSelection: PropTypes.func.isRequired,
    onDeleteSelection: PropTypes.func.isRequired
  };

  executeBatchOperation = async (openSelectionId, operation) => {
    const {selections} = this.props;
    const {queries} = getSelectionById(selections, openSelectionId);

    try {
      await applyOperation(operation, queries);
    } catch (e) {
      console.log(e);
    }
  };

  handleRetrySelection = openSelectionId => {
    this.executeBatchOperation(openSelectionId, OPERATION_TYPE.UPDATE_RETRIES);
  };

  handleCancelSelection = openSelectionId => {
    this.executeBatchOperation(openSelectionId, OPERATION_TYPE.CANCEL);
  };

  render() {
    const transitionTiming = 200;
    const {
      selections,
      openSelection,
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
          <TransitionGroup>
            {selections.length ? (
              selections.map(selection => {
                const {selectionId, instancesMap, totalCount} = selection;

                return (
                  <CSSTransition
                    key={selectionId}
                    classNames="transition"
                    timeout={transitionTiming}
                  >
                    <Styled.Li
                      data-test="selection-list-item"
                      key={selectionId}
                      transitionTiming={transitionTiming}
                    >
                      <Selection
                        isOpen={openSelection === selectionId}
                        selectionId={selectionId}
                        instances={instancesMap}
                        instanceCount={totalCount}
                        onRetry={() => this.handleRetrySelection(selectionId)}
                        onCancel={() => this.handleCancelSelection(selectionId)}
                        onToggle={() => onToggleSelection(selectionId)}
                        onDelete={() => onDeleteSelection(selectionId)}
                      />
                    </Styled.Li>
                  </CSSTransition>
                );
              })
            ) : (
              <CSSTransition classNames="transition" timeout={transitionTiming}>
                <Styled.NoSelectionWrapper
                  data-test="empty-selection-list-message"
                  transitionTiming={transitionTiming}
                >
                  {NO_SELECTIONS_MESSAGE}
                </Styled.NoSelectionWrapper>
              </CSSTransition>
            )}
          </TransitionGroup>
        </Styled.Ul>
      </React.Fragment>
    );
  }
}

export default withSelection(SelectionList);
