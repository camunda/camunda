import React from 'react';
import PropTypes from 'prop-types';

import StateIcon from 'modules/components/StateIcon';
import Dropdown from 'modules/components/Dropdown';

import {OPERATION_STATE, OPERATION_TYPE} from 'modules/constants';

import {getWorkflowName, getLatestOperation} from 'modules/utils/instance';
import {ReactComponent as Down} from 'modules/components/Icon/down.svg';
import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import {BADGE_TYPE} from 'modules/constants';

import * as Styled from './styled.js';

export default class Selection extends React.Component {
  static propTypes = {
    isOpen: PropTypes.bool.isRequired,
    selectionId: PropTypes.number.isRequired,
    instances: PropTypes.object.isRequired,
    instanceCount: PropTypes.number.isRequired,
    onToggle: PropTypes.func.isRequired,
    onRetry: PropTypes.func.isRequired,
    onCancel: PropTypes.func.isRequired,
    onDelete: PropTypes.func.isRequired
  };

  state = {operationState: '', numberOfNewInstances: 0};

  componentDidUpdate(prevProps) {
    if (this.props.instances.size > prevProps.instances.size) {
      const numberOfNewInstances = Math.abs(
        this.props.instances.size - prevProps.instances.size
      );
      this.setState({numberOfNewInstances});
    } else if (prevProps.isOpen && !this.props.isOpen) {
      this.setState({numberOfNewInstances: 0});
    }
  }

  operationsMap = {
    [OPERATION_TYPE.CANCEL]: {
      action: this.props.onCancel,
      label: 'Cancel',
      icon: <Styled.CancelIcon />
    },
    [OPERATION_TYPE.UPDATE_RETRIES]: {
      action: this.props.onRetry,
      label: 'Retry',
      icon: <Styled.RetryIcon />
    }
  };

  stopClickPropagation = evt => evt && evt.stopPropagation();

  handleOnClick = optionType => {
    this.setState({operationState: OPERATION_STATE.SCHEDULED});
    this.operationsMap[optionType].action();
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
    <Styled.Actions>
      <Styled.DropdownWrapper onClick={this.stopClickPropagation}>
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

  renderArrowIcon = isOpen => (
    <Styled.ArrowIcon isOpen={isOpen}>
      {isOpen ? <Down /> : <Right />}
    </Styled.ArrowIcon>
  );

  renderHeader = idString => {
    const {isOpen, selectionId, onToggle, instanceCount} = this.props;
    return (
      <Styled.Dt isOpen={isOpen}>
        {this.renderArrowIcon(isOpen)}
        <Styled.Heading role="heading">
          <Styled.SelectionToggle
            onClick={onToggle}
            isOpen={isOpen}
            id={`${idString}-toggle`}
            aria-expanded={isOpen}
            aria-controls={idString}
            aria-label={`Selection ${selectionId}, holding ${instanceCount} Instance${
              instanceCount !== 1 ? 's' : ''
            }`}
          >
            <Styled.Headline>Selection {selectionId}</Styled.Headline>
            <Styled.Badge isOpen={isOpen} type={BADGE_TYPE.SELECTIONS}>
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
    const {numberOfNewInstances} = this.state;
    return (
      <ul>
        {instances.map(([_, instanceDetails], index) => {
          const {state, type} = getLatestOperation(instanceDetails.operations);
          return (
            <Styled.Li key={index} isNew={index < numberOfNewInstances}>
              <Styled.StatusCell>
                <StateIcon instance={instanceDetails} />
              </Styled.StatusCell>
              <Styled.NameCell>
                {getWorkflowName(instanceDetails)}
              </Styled.NameCell>
              <Styled.IdCell>{instanceDetails.id}</Styled.IdCell>
              <Styled.ActionStatusCell>
                <Styled.InstanceActionStatus
                  operationState={this.state.operationState || state}
                  operationType={type}
                />
              </Styled.ActionStatusCell>
            </Styled.Li>
          );
        })}
      </ul>
    );
  };

  renderFooter = () => {
    const numberOfNotShownInstances =
      this.props.instanceCount - this.props.instances.size;
    return (
      <Styled.Footer>
        <Styled.MoreInstances>
          {numberOfNotShownInstances > 0 &&
            `${numberOfNotShownInstances} more Instance${
              numberOfNotShownInstances !== 1 ? 's' : ''
            }`}
        </Styled.MoreInstances>
      </Styled.Footer>
    );
  };

  render() {
    const idString = `selection-${this.props.selectionId}`;

    return (
      <Styled.Dl role="presentation">
        {this.renderHeader(idString)}
        {this.props.isOpen && (
          <Styled.Dd
            role="region"
            id={idString}
            aria-labelledby={`${idString}-toggle`}
          >
            {this.renderBody(idString)}
            {this.renderFooter()}
          </Styled.Dd>
        )}
      </Styled.Dl>
    );
  }
}
