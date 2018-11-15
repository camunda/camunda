import React from 'react';
import PropTypes from 'prop-types';

import IncidentStatistic from './IncidentStatistic';

import * as Styled from './styled';

function getVersions(workflows = []) {
  return workflows.map(item => item.version).join(', ');
}

export default class IncidentsByWorkflow extends React.Component {
  static propTypes = {
    incidents: PropTypes.array
  };

  render() {
    const {incidents} = this.props;
    return (
      <Styled.Ul>
        {incidents.map(item => {
          const versions = getVersions(item.workflows);
          const name = item.workflowName || item.bpmnProcessId;

          return (
            <Styled.Li key={item.bpmnProcessId}>
              <IncidentStatistic
                label={`${name} (Version ${versions})`}
                incidentsCount={item.instancesWithActiveIncidentsCount}
                activeCount={item.activeInstancesCount}
              />
            </Styled.Li>
          );
        })}
      </Styled.Ul>
    );
  }
}
