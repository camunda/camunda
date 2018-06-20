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
    filter: {running: true},
    filterCount: 0,
    selection: {
      list: new Set(),
      isBlacklist: false
    }
  };

  handleFilterChange = async change => {
    const filter = update(this.state.filter, change);
    this.setState({
      filter
    });
    // separate setState to not block UI while waiting for server response
    this.setState({
      filterCount: await getCount(filter)
    });
  };

  getSelectionCount = () => {
    const {
      filterCount,
      selection: {list, isBlacklist}
    } = this.state;

    return isBlacklist ? filterCount - list.size : list.size;
  };

  render() {
    return (
      <div>
        <Header
          active="instances"
          instances={14576}
          filters={this.state.filterCount}
          selections={this.getSelectionCount()}
          incidents={328}
        />
        <Styled.Filter>
          <Styled.Left>
            <Panel isRounded>
              <Panel.Header isRounded foldButtonType="left">
                Filters
              </Panel.Header>
              <Panel.Body>
                <InstancesFilter
                  filter={this.state.filter}
                  onChange={this.handleFilterChange}
                />
              </Panel.Body>
              <Panel.Footer />
            </Panel>
          </Styled.Left>
          <Styled.Right>
            <Styled.Top>
              <Panel isRounded>
                <Panel.Header isRounded>Process Definition Name</Panel.Header>
                <Panel.Body>Process Definition Name content</Panel.Body>
              </Panel>
            </Styled.Top>
            <Styled.Bottom>
              <InstancesListView
                instancesInFilter={this.state.filterCount}
                onSelectionUpdate={change => {
                  this.setState({
                    selection: update(this.state.selection, change)
                  });
                }}
                selection={this.state.selection}
                filter={this.state.filter}
              />
            </Styled.Bottom>
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
