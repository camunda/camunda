import React from 'react';

import StateIcon from 'modules/components/StateIcon';
import {getWorkflowName} from 'modules/utils/instance';
import {Down} from 'modules/components/Icon';

import * as Styled from './styled.js';

export default class Selection extends React.Component {
  constructor(props) {
    super(props);

    this.noOfShownInstances = 10;
    this.state = {instances: {}};
  }

  componentDidUpdate = async previousState => {
    if (previousState.instances !== this.props.instances) {
      this.setState({instances: this.props.instances});
    }
  };

  render() {
    return (
      <Styled.Selection>
        <Styled.Header>
          <Down />
          <span>Selection {this.props.index} </span>
          <span>count: {this.props.count}</span>
        </Styled.Header>
        <Styled.Body>
          {Object.keys(this.state.instances).length > 0 &&
            this.state.instances.map((instance, index) => {
              console.log(getWorkflowName(instance));
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
          {this.props.count - this.noOfShownInstances}
          <span>{' more Instances'}</span>
        </Styled.Footer>
      </Styled.Selection>
    );
  }
}
