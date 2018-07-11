import React from 'react';

import StateIcon from 'modules/components/StateIcon';
import {getWorkflowName} from 'modules/utils/instance';
import {Down} from 'modules/components/Icon';

import * as Styled from './styled.js';

export default class Selection extends React.Component {
  render() {
    return (
      <Styled.Selection>
        <Styled.Header>
          <Down />
          <span>Selection {this.props.index + 1} </span>
          <span>count: {this.props.count}</span>
          <span onClick={this.props.onRetry}>retry</span>
          <span onClick={this.props.onDelete}>delete</span>
        </Styled.Header>
        <Styled.Body>
          {this.props.instances.map((instance, index) => {
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
          {this.props.count - 10}
          <span>{' more Instances'}</span>
        </Styled.Footer>
      </Styled.Selection>
    );
  }
}
