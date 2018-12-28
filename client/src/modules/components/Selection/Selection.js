import React from 'react';
import PropTypes from 'prop-types';
import {CSSTransition, TransitionGroup} from 'react-transition-group';

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
    const {instances: newInstances} = this.props;
    const {instances: prevInstances, isOpen: wasOpen} = prevProps;

    if (newInstances.size > prevInstances.size) {
      const numberOfNewInstances = Math.abs(
        newInstances.size - prevInstances.size
      );
      this.setState({numberOfNewInstances});
    } else if (!this.props.isOpen && wasOpen) {
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
    const transitionTiming = 800;
    return (
      <ul>
        <TransitionGroup>
          {instances.map(([_, instanceDetails], index) => {
            const {state, type} = getLatestOperation(
              instanceDetails.operations
            );
            return (
              <CSSTransition
                data-test="addInstanceTransition"
                classNames="transition"
                key={index}
                timeout={transitionTiming}
              >
                <Styled.Li key={index} transitionTiming={transitionTiming}>
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
              </CSSTransition>
            );
          })}
        </TransitionGroup>
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
    const transitionTiming = {enter: 200, exit: 100};
    return (
      <Styled.Dl role="presentation">
        {this.renderHeader(idString)}
        <CSSTransition
          data-test="openSelectionTransition"
          classNames="transition"
          in={this.props.isOpen}
          timeout={transitionTiming}
          mountOnEnter
          unmountOnExit
        >
          <Styled.Dd
            role="region"
            id={idString}
            transitionTiming={transitionTiming}
            aria-labelledby={`${idString}-toggle`}
          >
            {this.renderBody(idString)}
            {this.renderFooter()}
          </Styled.Dd>
        </CSSTransition>
      </Styled.Dl>
    );
  }
}
