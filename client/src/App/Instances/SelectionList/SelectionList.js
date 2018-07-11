import React from 'react';

import * as Styled from './styled.js';
import {parseFilterForRequest} from 'modules/utils/filter';

import {
  fetchWorkflowInstances,
  fetchWorkflowInstancesCount
} from 'modules/api/instances';

import Selection from '../Selection';

export default class SelectionList extends React.Component {
  state = {instances: {}};

  componentDidMount = async () => {
    const instances = await fetchWorkflowInstances(
      parseFilterForRequest({active: false, incidents: true}),
      1,
      10
    );
    this.setState({instances});

    const count = await fetchWorkflowInstancesCount(
      parseFilterForRequest({active: false, incidents: true})
    );
    this.setState({count});
  };

  render() {
    const selections = this.props.selections;
    return (
      <Styled.SelectionList>
        {selections.map((selection, index) => (
          <Selection
            key={index}
            index={index}
            selection={selection}
            instances={this.state.instances}
            count={this.state.count}
          />
        ))}
      </Styled.SelectionList>
    );
  }
}
