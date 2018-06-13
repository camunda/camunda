import React, {Component} from 'react';

import {Header} from '../Header';

import Panel from 'modules/components/Panel';

import {InstancesFilter} from './InstancesFilter';
import {InstancesListView} from './InstancesListView';

import * as Styled from './styled.js';

console.log(Panel);

export default class Filter extends Component {
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
              <Panel.Header headline="Filters" foldButtonType="left" />
              <InstancesFilter />
              <Panel.Footer />
            </Panel>
          </Styled.Left>
          <Styled.Right>
            <Panel>
              <Panel.Header headline="Process Definition Name" />
            </Panel>
            <InstancesListView instancesInFilter={9263} />
          </Styled.Right>
        </Styled.Filter>
      </div>
    );
  }
}
