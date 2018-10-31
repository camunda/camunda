import React, {Fragment} from 'react';
import PropTypes from 'prop-types';
import StateIcon from 'modules/components/StateIcon';
import Dropdown from 'modules/components/Dropdown';

import {getWorkflowName} from 'modules/utils/instance';
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

  stopClickPropagation = evt => evt && evt.stopPropagation();

  renderArrowIcon = isOpen => (
    <Styled.ArrowIcon>{isOpen ? <Down /> : <Right />}</Styled.ArrowIcon>
  );

  renderBody = instances => {
    return instances.map((instance, index) => (
      <Styled.Instance key={index}>
        <StateIcon {...{instance}} />
        <Styled.WorkflowName>{getWorkflowName(instance)}</Styled.WorkflowName>
        <Styled.InstanceId>{instance.id}</Styled.InstanceId>
      </Styled.Instance>
    ));
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

  renderRetryLabel = () => (
    <div>
      <Styled.RetryIcon /> Retry
    </div>
  );

  renderCancelLabel = () => (
    <div>
      <Styled.CancelIcon /> Cancel
    </div>
  );

  renderActions = (onRetry, onCancel, onDelete) => (
    <Styled.Actions>
      <Styled.DropdownTrigger onClick={this.stopClickPropagation}>
        <Dropdown
          label={<Styled.BatchIcon />}
          buttonStyles={Styled.dropDownButtonStyles}
        >
          <Dropdown.Option
            data-test="cancel-dropdown-option"
            onClick={onCancel}
            label={this.renderCancelLabel()}
          />
          <Dropdown.Option
            data-test="retry-dropdown-option"
            onClick={onRetry}
            label={this.renderRetryLabel()}
          />
        </Dropdown>
      </Styled.DropdownTrigger>
      <Styled.DeleteIcon onClick={onDelete} />
    </Styled.Actions>
  );

  render() {
    const {
      isOpen,
      selectionId,
      onRetry,
      onCancel,
      onDelete,
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

          {isOpen && renderActions(onRetry, onCancel, onDelete)}
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
