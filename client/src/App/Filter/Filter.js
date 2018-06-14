import React, {Component} from 'react';
import update from 'immutability-helper';

import Header from '../Header';

import Panel from 'modules/components/Panel';

import InstancesFilter from './InstancesFilter';
import InstancesListView from './InstancesListView';

import {getCount} from './api';

import * as Styled from './styled.js';

export default class Filter extends Component {
  state = {
    filter: {running: true}
  };

  handleFilterChange = async change => {
    const filter = update(this.state.filter, change);
    this.setState({
      filter,
      filterCount: await getCount(filter)
    });
  };

  render() {
    return (
      <div>
        <Header
          active="instances"
          instances={14576}
          filters={this.state.filterCount}
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
              instancesInFilter={this.state.filterCount}
              filter={this.state.filter}
            />
          </Styled.Right>
        </Styled.Filter>
      </div>
    );
  }

  async componentDidMount() {
    this.setState({
      filterCount: await getCount(this.state.filter)
    });
  }
}
