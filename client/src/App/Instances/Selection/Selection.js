import React, {Fragment} from 'react';
import PropTypes from 'prop-types';
import StateIcon from 'modules/components/StateIcon';
import Dropdown from 'modules/components/Dropdown';

import {getWorkflowName} from 'modules/utils/instance';
import {Down, Right, Batch, Retry} from 'modules/components/Icon';

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
    count: PropTypes.number.isRequired,
    onClick: PropTypes.func.isRequired,
    onRetry: PropTypes.func.isRequired,
    onDelete: PropTypes.func.isRequired
  };

  getArrowIcon = isOpen => (isOpen ? <Down /> : <Right />);

  getBody = instances => {
    return instances.map((instance, index) => (
      // <Styled.Instance key={instance.id}> TODO: replace index key with id when realdata is available.
      <Styled.Instance key={index}>
        <StateIcon {...{instance}} />
        <Styled.WorkflowName>{getWorkflowName(instance)}</Styled.WorkflowName>
        <Styled.InstanceId>{instance.id}</Styled.InstanceId>
      </Styled.Instance>
    ));
  };

  getFooter = (count, numberOfDisplayedInstances) => (
    <Styled.Footer>
      <Styled.MoreInstances>
        {count - numberOfDisplayedInstances + ' more Instances'}
      </Styled.MoreInstances>
    </Styled.Footer>
  );

  getActions = (onRetry, onDelete) => (
    <Styled.Actions>
      <Styled.DropdownTrigger onClick={evt => evt && evt.stopPropagation()}>
        <Dropdown label={<Batch />}>
          <Dropdown.Option data-test="logout-button" onClick={onRetry}>
            <Retry />
            <Styled.OptionLabel>Retry</Styled.OptionLabel>
          </Dropdown.Option>
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
      onClick,
      instances,
      count
    } = this.props;
    const {getArrowIcon, getBody, getActions, getFooter} = this;
    return (
      <Styled.Selection>
        <Styled.Header {...{onClick, isOpen}}>
          {getArrowIcon(isOpen)}
          <Styled.Headline>Selection {selectionId + 1}</Styled.Headline>
          {/*TODO: ICON <span>{count}</span> */}
          {isOpen && getActions(onRetry, onDelete)}
        </Styled.Header>
        {isOpen && (
          <Fragment>
            {getBody(instances, count)}
            {getFooter(count, instances.length)}
          </Fragment>
        )}
      </Styled.Selection>
    );
  }
}
