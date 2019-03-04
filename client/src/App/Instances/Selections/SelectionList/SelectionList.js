/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {CSSTransition} from 'react-transition-group';

import Selection from 'modules/components/Selection';
import {withSelection} from 'modules/contexts/SelectionContext';
import {getInstancesIdsFromSelections} from 'modules/contexts/SelectionContext/service';
import {withPoll} from 'modules/contexts/InstancesPollContext';
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
      await applyOperation(operation, selectionById.queries);
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
    const timeout = 200;
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
        <Styled.TransitionGroup component={'ul'}>
          {selections.length ? (
            selections.map(selection => {
              const {selectionId, instancesMap, totalCount} = selection;

              return (
                <CSSTransition
                  key={selectionId}
                  classNames="transition"
                  timeout={timeout}
                >
                  <Styled.Li
                    key={selectionId}
                    timeout={timeout}
                    data-test="selection-list-item"
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
            <CSSTransition classNames="transition" timeout={timeout}>
              <Styled.NoSelectionWrapper
                data-test="empty-selection-list-message"
                timeout={timeout}
              >
                {NO_SELECTIONS_MESSAGE}
              </Styled.NoSelectionWrapper>
            </CSSTransition>
          )}
        </Styled.TransitionGroup>
      </React.Fragment>
    );
  }
}

export default withPoll(withSelection(SelectionList));
