import React, {Fragment} from 'react';

import StateIcon from 'modules/components/StateIcon';
import {getWorkflowName} from 'modules/utils/instance';
import {Down, Right, Batch} from 'modules/components/Icon';

import * as Styled from './styled.js';

export default class Selection extends React.Component {
  AddBody = ({instances, count}) => (
    <Fragment>
      <Styled.Body>
        {instances.map((instance, index) => {
          return (
            <div key={index}>
              <StateIcon instance={instance} />
              <span>{getWorkflowName(instance)}</span>
              {instance.id}
            </div>
          );
        })}
      </Styled.Body>
      <Styled.Footer>
        {count - instances.length}
        <span>{' more Instances'}</span>
      </Styled.Footer>
    </Fragment>
  );

  render() {
    const {isOpen, onRetry, onDelete, onClick, id} = this.props;
    return (
      <Styled.Selection>
        <Styled.Header {...{onClick, isOpen}}>
          {isOpen ? <Down /> : <Right />}
          <Styled.Headline>Selection {id + 1} </Styled.Headline>
          {/*TODO: ICON <span>{count}</span> */}
          {isOpen && (
            <Styled.Actions>
              <Styled.DropdownTrigger onClick={onRetry}>
                <Batch />
                <Down />
              </Styled.DropdownTrigger>

              <Styled.DeleteIcon onClick={onDelete} />
            </Styled.Actions>
          )}
        </Styled.Header>
        {isOpen && this.AddBody(this.props)}
      </Styled.Selection>
    );
  }
}
