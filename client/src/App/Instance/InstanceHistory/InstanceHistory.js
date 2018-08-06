import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import Copyright from 'modules/components/Copyright';

import InstanceLog from './InstanceLog';
import InstanceEvents from './InstanceEvents';
import * as Styled from './styled';

export default class InstanceHistory extends React.Component {
  static propTypes = {
    instance: PropTypes.object.isRequired,
    activitiesDetails: PropTypes.object
  };

  render() {
    return (
      <SplitPane.Pane {...this.props}>
        <SplitPane.Pane.Header>Instance History</SplitPane.Pane.Header>
        <Styled.PaneBody>
          <InstanceLog
            instance={this.props.instance}
            activitiesDetails={this.props.activitiesDetails}
          />
          <InstanceEvents
            instance={this.props.instance}
            activitiesDetails={this.props.activitiesDetails}
          />
          <Styled.Section>C</Styled.Section>
        </Styled.PaneBody>
        <Styled.PaneFooter>
          <Copyright />
        </Styled.PaneFooter>
      </SplitPane.Pane>
    );
  }
}
