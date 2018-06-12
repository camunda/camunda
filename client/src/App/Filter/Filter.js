import React, {Component} from 'react';

import {Header} from '../Header';

import {Panel} from './Panel';
import {PanelHeader} from './PanelHeader';
import {PanelFooter} from './PanelFooter';
import {InstancesFilter} from './InstancesFilter';
import {InstancesListView} from './InstancesListView';

import * as Styled from './styled.js';

class Filter extends Component {
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
              <PanelHeader headline="Filters" foldButtonType="left" />
              <InstancesFilter type="running" />
              <PanelFooter />
            </Panel>
          </Styled.Left>
          <Styled.Right>
            <Panel>
              <PanelHeader headline="Process Definition Name" />
            </Panel>
            <InstancesListView instancesInFilter={9263} />
          </Styled.Right>
        </Styled.Filter>
      </div>
    );
  }
}

export default Filter;
