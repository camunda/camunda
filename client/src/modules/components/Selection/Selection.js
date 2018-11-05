import React, {Fragment} from 'react';
import PropTypes from 'prop-types';

import StateIcon from 'modules/components/StateIcon';
import Dropdown from 'modules/components/Dropdown';

import {OPERATION_STATE, OPERATION_TYPE} from 'modules/constants';

import ActionStatus from 'modules/components/ActionStatus';

import {getWorkflowName, getLatestOperation} from 'modules/utils/instance';
import {ReactComponent as Down} from 'modules/components/Icon/down.svg';
import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import {BADGE_TYPE} from 'modules/constants';

import * as Styled from './styled.js';

export default class Selection extends React.Component {
  static propTypes = {
    isOpen: PropTypes.bool.isRequired,
    selectionId: PropTypes.number.isRequired,
    instances: PropTypes.arrayOf(
      PropTypes.shape({
        id: PropTypes.string.isRequired,
        state: PropTypes.string.isRequired,
        workflowId: PropTypes.string.isRequired
      }).isRequired
    ).isRequired,
    instanceCount: PropTypes.number.isRequired,
    onToggle: PropTypes.func.isRequired,
    onRetry: PropTypes.func.isRequired,
    onCancel: PropTypes.func.isRequired,
    onDelete: PropTypes.func.isRequired
  };

  constructor(props) {
    super(props);
    this.availableOperations = [
      OPERATION_TYPE.CANCEL,
      OPERATION_TYPE.UPDATE_RETRIES
    ];
    this.state = {operationState: ''};
  }

  stopClickPropagation = evt => evt && evt.stopPropagation();

  handleOnClick = optionType => {
    const actionMap = {
      [OPERATION_TYPE.UPDATE_RETRIES]: this.props.onRetry,
      [OPERATION_TYPE.CANCEL]: this.props.onCancel
    };
    const callOperation = actionMap[optionType];

    this.setState({operationState: OPERATION_STATE.SCHEDULED});
    callOperation();
  };

  renderArrowIcon = isOpen => (
    <Styled.ArrowIcon>{isOpen ? <Down /> : <Right />}</Styled.ArrowIcon>
  );

  renderBody = instances => {
    return instances.map((instance, index) => {
      const {state, type} = getLatestOperation(instance.operations);
      return (
        <Styled.Instance key={index}>
          <StateIcon {...{instance}} />
          <Styled.WorkflowName>{getWorkflowName(instance)}</Styled.WorkflowName>
          <Styled.InstanceId>{instance.id}</Styled.InstanceId>
          <ActionStatus
            operationState={this.state.operationState || state}
            operationType={type}
          />
        </Styled.Instance>
      );
    });
  };

  renderFooter = (instanceCount, numberOfDisplayedInstances) => {
    const difference = instanceCount - numberOfDisplayedInstances;
    return (
      <Styled.Footer>
        <Styled.MoreInstances>
          {difference > 0 &&
            `${difference} more Instance${difference !== 1 ? 's' : ''}`}
        </Styled.MoreInstances>
      </Styled.Footer>
    );
  };

  renderLabel = type => {
    const labelMap = {
      [OPERATION_TYPE.UPDATE_RETRIES]: 'Retry',
      [OPERATION_TYPE.CANCEL]: 'Cancel'
    };
    const iconMap = {
      [OPERATION_TYPE.UPDATE_RETRIES]: <Styled.RetryIcon />,
      [OPERATION_TYPE.CANCEL]: <Styled.CancelIcon />
    };

    return (
      <div>
        {iconMap[type]}
        {labelMap[type]}
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
      <Styled.DropdownTrigger onClick={this.stopClickPropagation}>
        <Dropdown
          label={<Styled.BatchIcon />}
          buttonStyles={Styled.dropDownButtonStyles}
        >
          {this.availableOperations.map(this.renderOption)}
        </Dropdown>
      </Styled.DropdownTrigger>
      <Styled.DeleteIcon onClick={this.props.onDelete} />
    </Styled.Actions>
  );

  render() {
    const {
      isOpen,
      selectionId,
      onToggle,
      instances,
      instanceCount
    } = this.props;
    const {renderArrowIcon, renderBody, renderActions, renderFooter} = this;
    return (
      <Styled.Selection>
        <Styled.Header onClick={onToggle} {...{isOpen}}>
          {renderArrowIcon(isOpen)}
          <Styled.Headline>Selection {selectionId}</Styled.Headline>
          <Styled.Badge isOpen={isOpen} type={BADGE_TYPE.SELECTIONS}>
            {instanceCount}
          </Styled.Badge>
          {isOpen && renderActions()}
        </Styled.Header>
        {isOpen && (
          <Fragment>
            {renderBody(instances, instanceCount)}
            {renderFooter(instanceCount, instances.length)}
          </Fragment>
        )}
      </Styled.Selection>
    );
  }
}
