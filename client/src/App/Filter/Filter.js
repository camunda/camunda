import React, {Component} from 'react';
import update from 'immutability-helper';

import Header from '../Header';

import Panel from 'modules/components/Panel';

import InstancesFilter from './InstancesFilter';
import InstancesListView from './InstancesListView';

import * as Styled from './styled.js';

export default class Filter extends Component {
  state = {
    filter: {}
  };

  handleFilterChange = change =>
    this.setState({filter: update(this.state.filter, change)});

  render() {
    return (
      <div>
        <Header
          active="instances"
          instances={14576}
          filters={9263}
          selections={24}
          incidents={328}
        />
        <Styled.Filter>
          <Styled.Left>
            <Panel>
              <Panel.Header foldButtonType="left">Filters</Panel.Header>
              <InstancesFilter
                filter={this.state.filter}
                onChange={this.handleFilterChange}
              />
              <Panel.Footer />
            </Panel>
          </Styled.Left>
          <Styled.Right>
            <Panel>
              <Panel.Header>Process Definition Name</Panel.Header>
            </Panel>
            <InstancesListView
              instancesInFilter={9263}
              filter={this.state.filter}
            />
          </Styled.Right>
        </Styled.Filter>
      </div>
    );
  }
}
