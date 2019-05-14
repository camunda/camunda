/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import StateIcon from 'modules/components/StateIcon';
import Dropdown from 'modules/components/Dropdown';
import {TransitionGroup} from 'modules/components/Transition';

import {OPERATION_STATE, OPERATION_TYPE} from 'modules/constants';
import {getWorkflowName, getLatestOperation} from 'modules/utils/instance';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {BADGE_TYPE} from 'modules/constants';

import * as Styled from './styled.js';

export default class Selection extends React.Component {
  static propTypes = {
    isOpen: PropTypes.bool.isRequired,
    selectionId: PropTypes.number.isRequired,
    instances: PropTypes.object.isRequired,
    instanceCount: PropTypes.number.isRequired,
    transitionTimeOut: PropTypes.object.isRequired,
    onToggle: PropTypes.func.isRequired,
    onRetry: PropTypes.func.isRequired,
    onCancel: PropTypes.func.isRequired,
    onDelete: PropTypes.func.isRequired
  };

  state = {
    isOperationStarted: false,
    showExitTransition: false
  };

  componentDidUpdate(prevProps, prevState) {
    const isNewInstancesList = prevProps.instances !== this.props.instances;

    // detect if the list has been updated since the operation and reset the spinner
    if (isNewInstancesList) {
      this.state.isOperationStarted &&
        this.setState({isOperationStarted: false});
    }
  }

  operationsMap = {
    [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: {
      action: this.props.onCancel,
      label: 'Cancel',
      icon: <Styled.CancelIcon />
    },
    [OPERATION_TYPE.RESOLVE_INCIDENT]: {
      action: this.props.onRetry,
      label: 'Retry',
      icon: <Styled.RetryIcon />
    }
  };

  handleOnClick = optionType => {
    this.setState({
      isOperationStarted: true
    });
    this.operationsMap[optionType].action();
  };

  handleOnHeaderClick = () => {
    const {isOpen, onToggle} = this.props;

    if (isOpen) {
      this.setState({showExitTransition: true}, () => {
        onToggle();
      });
    } else {
      onToggle();
    }
  };

  renderLabel = type => {
    const {icon, label} = this.operationsMap[type];
    return (
      <div>
        {icon} {label}
      </div>
    );
  };

  renderOption = operationType => {
    return (
      <Dropdown.Option
        key={operationType}
        data-test={`${operationType}-dropdown-option`}
        onClick={() => this.handleOnClick(operationType)}
        label={this.renderLabel(operationType)}
      />
    );
  };

  renderActions = () => (
    <Styled.Actions data-test="actions">
      <Styled.DropdownWrapper onClick={evt => evt && evt.stopPropagation()}>
        <Dropdown
          aria-label="Batch Operations"
          label={<Styled.BatchIcon />}
          buttonStyles={Styled.dropDownButtonStyles}
        >
          {Object.keys(this.operationsMap).map(this.renderOption)}
        </Dropdown>
      </Styled.DropdownWrapper>
      <Styled.ActionButton
        aria-label={`Drop Selection ${this.props.selectionId}`}
        onClick={this.props.onDelete}
      >
        <Styled.DeleteIcon />
      </Styled.ActionButton>
    </Styled.Actions>
  );

  renderHeader = idString => {
    const {isOpen, selectionId, instanceCount} = this.props;
    return (
      <Styled.Dt isExpanded={isOpen}>
        <Styled.Heading role="heading">
          <Styled.SelectionToggle
            data-test="selection-toggle"
            onClick={this.handleOnHeaderClick}
            isExpanded={isOpen}
            expandTheme={isOpen ? 'selectionExpanded' : 'selectionCollapsed'}
            id={`${idString}-toggle`}
            aria-expanded={isOpen}
            aria-controls={idString}
            aria-label={`Selection ${selectionId}, holding ${pluralSuffix(
              instanceCount,
              'Instance'
            )}`}
          >
            <Styled.Headline>Selection {selectionId}</Styled.Headline>
            <Styled.Badge isExpanded={isOpen} type={BADGE_TYPE.SELECTIONS}>
              {instanceCount}
            </Styled.Badge>
          </Styled.SelectionToggle>
        </Styled.Heading>
        {isOpen && this.renderActions()}
      </Styled.Dt>
    );
  };

  renderBody = () => {
    const instances = [...this.props.instances];
    const timeout = 800;

    return (
      <Styled.Ul>
        <TransitionGroup component={null}>
          {instances.map(([_, instanceDetails], index) => {
            const {type, state} = getLatestOperation(
              instanceDetails.operations
            );
            return (
              <Styled.AddInstanceTransition
                data-test="addInstanceTransition"
                key={index}
                timeout={timeout}
              >
                <Styled.Li>
                  <Styled.StatusCell>
                    <StateIcon state={instanceDetails.state} />
                  </Styled.StatusCell>
                  <Styled.NameCell>
                    {getWorkflowName(instanceDetails)}
                  </Styled.NameCell>
                  <Styled.IdCell>{instanceDetails.id}</Styled.IdCell>
                  <Styled.ActionStatusCell>
                    <Styled.InstanceActionStatus
                      instance={instanceDetails}
                      operationState={
                        this.state.isOperationStarted
                          ? OPERATION_STATE.SCHEDULED
                          : state
                      }
                      operationType={type}
                    />
                  </Styled.ActionStatusCell>
                </Styled.Li>
              </Styled.AddInstanceTransition>
            );
          })}
        </TransitionGroup>
      </Styled.Ul>
    );
  };

  renderFooter = () => {
    const numberOfNotShownInstances =
      this.props.instanceCount - this.props.instances.size;

    return (
      <Styled.Footer>
        <Styled.MoreInstances>
          {numberOfNotShownInstances > 0 &&
            pluralSuffix(numberOfNotShownInstances, 'more Instance')}
        </Styled.MoreInstances>
      </Styled.Footer>
    );
  };

  render() {
    const {isOpen, selectionId, transitionTimeOut} = this.props;
    const {showExitTransition} = this.state;
    const idString = `selection-${selectionId}`;

    return (
      <Styled.Dl role="presentation">
        {this.renderHeader(idString)}
        <Styled.OpenSelectionTransition
          data-test="openSelectionTransition"
          in={isOpen}
          timeout={transitionTimeOut}
          exit={showExitTransition}
          onExited={() =>
            showExitTransition && this.setState({showExitTransition: false})
          }
          mountOnEnter
          unmountOnExit
        >
          <Styled.Dd
            role="region"
            id={idString}
            aria-labelledby={`${idString}-toggle`}
          >
            {this.renderBody(idString)}
            {this.renderFooter()}
          </Styled.Dd>
        </Styled.OpenSelectionTransition>
      </Styled.Dl>
    );
  }
}
