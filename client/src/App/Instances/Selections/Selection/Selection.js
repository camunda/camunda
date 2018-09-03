import React, {Fragment} from 'react';
import PropTypes from 'prop-types';
import StateIcon from 'modules/components/StateIcon';
import Dropdown from 'modules/components/Dropdown';

import {getWorkflowName} from 'modules/utils/instance';
import {BADGE_TYPE} from 'modules/constants';
import {Down, Right, Batch} from 'modules/components/Icon';

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
    onDelete: PropTypes.func.isRequired
  };

  stopClickPropagation = evt => evt && evt.stopPropagation();

  renderArrowIcon = isOpen => (isOpen ? <Down /> : <Right />);

  renderBody = instances => {
    return instances.map((instance, index) => (
      <Styled.Instance key={index}>
        <StateIcon {...{instance}} />
        <Styled.WorkflowName>{getWorkflowName(instance)}</Styled.WorkflowName>
        <Styled.InstanceId>{instance.id}</Styled.InstanceId>
      </Styled.Instance>
    ));
  };

  renderFooter = (instanceCount, numberOfDisplayedInstances) => (
    <Styled.Footer>
      <Styled.MoreInstances>
        {instanceCount - numberOfDisplayedInstances + ' more Instances'}
      </Styled.MoreInstances>
    </Styled.Footer>
  );

  renderRetryLabel = () => (
    <div>
      <Styled.RetryIcon />Retry
    </div>
  );

  renderActions = (onRetry, onDelete) => (
    <Styled.Actions>
      <Styled.DropdownTrigger onClick={this.stopClickPropagation}>
        <Dropdown label={<Batch />}>
          <Dropdown.Option onClick={onRetry} label={this.renderRetryLabel()} />
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
          <Styled.Badge
            type={
              isOpen ? BADGE_TYPE.OPENSELECTIONHEAD : BADGE_TYPE.SELECTIONHEAD
            }
            badgeContent={instanceCount}
          />

          {isOpen && renderActions(onRetry, onDelete)}
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
