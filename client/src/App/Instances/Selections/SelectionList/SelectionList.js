/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Selection from 'modules/components/Selection';
import {withSelection} from 'modules/contexts/SelectionContext';
import {getInstancesIdsFromSelections} from 'modules/contexts/SelectionContext/service';
import {withPoll} from 'modules/contexts/InstancesPollContext';
import {getSelectionById} from 'modules/utils/selection';
import {applyBatchOperation} from 'modules/api/instances';
import {NO_SELECTIONS_MESSAGE} from './constants';
import {MESSAGES_TYPE, OPERATION_TYPE} from 'modules/constants';

import {TransitionGroup} from 'modules/components/Transition';

import * as Styled from './styled.js';

class SelectionList extends React.Component {
  static propTypes = {
    selections: PropTypes.array.isRequired,
    openSelection: PropTypes.number,
    onToggleSelection: PropTypes.func.isRequired,
    onDeleteSelection: PropTypes.func.isRequired,
    polling: PropTypes.object
  };

  componentDidUpdate(prevProps, prevState) {
    // https://app.camunda.com/jira/browse/OPE-434
    // the logic should normaly stay in componentDidMount
    // as we only want to check when it renders the first time;
    // Because first render is with outdated data from localStorage (see SelectionContext)
    // we added here the code as a quick fix
    if (prevProps.selections !== this.props.selections) {
      const ids = this.getInstancesWithActiveOperation();

      Boolean(ids.length) && this.props.polling.addIds(ids);
    }
  }

  getInstancesWithActiveOperation = () => {
    let instancesWithActiveOperations = [];

    this.props.selections.forEach(selection => {
      const activeInstances = [...selection.instancesMap.values()].filter(
        item => item.hasActiveOperation
      );
      instancesWithActiveOperations = [
        ...instancesWithActiveOperations,
        ...activeInstances
      ];
    });

    return instancesWithActiveOperations.map(item => item.id);
  };

  executeBatchOperation = async (openSelectionId, operation) => {
    const {selections} = this.props;
    const selectionById = getSelectionById(selections, openSelectionId);
    try {
      await applyBatchOperation(operation, selectionById.queries);
      this.sendIdsForPolling(selectionById);
    } catch (e) {
      console.log(e);
    }
  };

  sendIdsForPolling = selection => {
    let ids = [];

    ids = getInstancesIdsFromSelections([selection]);
    this.props.polling.addIds(ids);
  };

  handleRetrySelection = openSelectionId => {
    this.executeBatchOperation(
      openSelectionId,
      OPERATION_TYPE.RESOLVE_INCIDENT
    );
  };

  handleCancelSelection = openSelectionId => {
    this.executeBatchOperation(
      openSelectionId,
      OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
    );
  };

  render() {
    const {
      selections,
      openSelection,
      onToggleSelection,
      onDeleteSelection
    } = this.props;

    const noTimeout = {enter: 0, exit: 0};
    return (
      <React.Fragment>
        <Styled.Ul>
          <TransitionGroup component={null}>
            {selections.length === 10 && (
              <Styled.Transition timeout={{enter: 100, exit: 100}}>
                <Styled.MessageWrapper>
                  <Styled.SelectionMessage
                    type={MESSAGES_TYPE.DROP_SELECTION}
                  />
                </Styled.MessageWrapper>
              </Styled.Transition>
            )}
            {selections.length ? (
              selections.map(selection => {
                const {selectionId, instancesMap, totalCount} = selection;
                const isOpen = openSelection === selectionId;
                const timeout = {
                  enter: -instancesMap.size * 20 + 600,
                  exit: 100
                };

                return (
                  <Styled.Transition
                    key={selectionId}
                    timeout={timeout}
                    exit={
                      !(selections.length === 1 || selections.length === 10)
                    }
                  >
                    <Styled.Li data-test="selection-list-item">
                      <Selection
                        key={selectionId}
                        isOpen={isOpen}
                        selectionId={selectionId}
                        instances={instancesMap}
                        transitionTimeOut={timeout}
                        instanceCount={totalCount}
                        onRetry={() => this.handleRetrySelection(selectionId)}
                        onCancel={() => this.handleCancelSelection(selectionId)}
                        onToggle={() => onToggleSelection(selectionId)}
                        onDelete={() => onDeleteSelection(selectionId)}
                      />
                    </Styled.Li>
                  </Styled.Transition>
                );
              })
            ) : (
              <Styled.Transition timeout={noTimeout}>
                <Styled.NoSelectionWrapper data-test="empty-selection-list-message">
                  {NO_SELECTIONS_MESSAGE}
                </Styled.NoSelectionWrapper>
              </Styled.Transition>
            )}
          </TransitionGroup>
        </Styled.Ul>
      </React.Fragment>
    );
  }
}

export default withPoll(withSelection(SelectionList));
