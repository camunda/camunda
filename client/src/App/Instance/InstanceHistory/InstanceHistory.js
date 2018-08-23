import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import Copyright from 'modules/components/Copyright';
import {HEADER} from 'modules/constants';
import {fetchEvents} from 'modules/api/events';

import InstanceLog from './InstanceLog';
import InstanceEvents from './InstanceEvents';
import {getGroupedEvents} from './service';
import * as Styled from './styled';

export default class InstanceHistory extends React.Component {
  static propTypes = {
    instance: PropTypes.object.isRequired,
    activitiesDetails: PropTypes.object
  };

  state = {
    selectedLogEntry: HEADER,
    events: null,
    groupedEvents: null
  };

  async componentDidMount() {
    const events = await fetchEvents(this.props.instance.id);
    this.setState({events});
  }

  componentDidUpdate(prevProps, prevState) {
    const {activitiesDetails} = this.props;
    const {events, selectedLogEntry} = this.state;

    if (!activitiesDetails || !events) {
      return;
    }

    const haveActivitiesDetailsChanged =
      activitiesDetails !== prevProps.activitiesDetails;
    const hasSelectedLogEntryChanged =
      selectedLogEntry !== prevState.selectedLogEntry;
    const haveEventsChanged = events !== prevState.events;

    if (
      haveActivitiesDetailsChanged ||
      haveEventsChanged ||
      hasSelectedLogEntryChanged
    ) {
      const groupedEvents = getGroupedEvents({
        events: this.state.events,
        activitiesDetails,
        selectedLogEntry
      });

      this.setState({groupedEvents});
    }
  }

  handleSelectedLogEntry = selectedLogEntry => {
    this.setState({selectedLogEntry});
  };

  render() {
    return (
      <SplitPane.Pane {...this.props}>
        <SplitPane.Pane.Header>Instance History</SplitPane.Pane.Header>
        <Styled.PaneBody>
          <InstanceLog
            instance={this.props.instance}
            activitiesDetails={this.props.activitiesDetails}
            selectedLogEntry={this.state.selectedLogEntry}
            onSelect={this.handleSelectedLogEntry}
          />
          <InstanceEvents groupedEvents={this.state.groupedEvents} />
          <Styled.Section>C</Styled.Section>
        </Styled.PaneBody>
        <Styled.PaneFooter>
          <Copyright />
        </Styled.PaneFooter>
      </SplitPane.Pane>
    );
  }
}
