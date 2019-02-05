import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import Copyright from 'modules/components/Copyright';
import {FlowNodeTimeStampProvider} from 'modules/contexts/FlowNodeTimeStampContext';

import * as Styled from './styled';

import TimeStampPill from './TimeStampPill';

export default class InstanceHistory extends React.PureComponent {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  render() {
    return (
      <SplitPane.Pane {...this.props}>
        <FlowNodeTimeStampProvider>
          <Styled.PaneHeader>
            <Styled.Headline>Instance History</Styled.Headline>
            <Styled.Pills>
              <TimeStampPill />
            </Styled.Pills>
          </Styled.PaneHeader>
          <Styled.PaneBody>{this.props.children}</Styled.PaneBody>
          <Styled.PaneFooter>
            <Copyright />
          </Styled.PaneFooter>
        </FlowNodeTimeStampProvider>
      </SplitPane.Pane>
    );
  }
}
