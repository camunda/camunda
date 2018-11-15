import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';
import Collapse from './Collapse';

function getVersions(workflows = []) {
  return workflows.map(item => item.version).join(', ');
}

export default class IncidentsByWorkflow extends React.Component {
  static propTypes = {
    incidents: PropTypes.array
  };

  getVersionStatistics = items => {
    return (
      <Styled.Ul>
        {items.map(item => (
          <Styled.VersionLi key={item.workflowId}>
            <Styled.IncidentStatistic
              label={`Version ${item.version}`}
              incidentsCount={item.instancesWithActiveIncidentsCount}
              activeCount={item.activeInstancesCount}
              perUnit
            />
          </Styled.VersionLi>
        ))}
      </Styled.Ul>
    );
  };

  render() {
    const {incidents} = this.props;
    return (
      <ul>
        {incidents.map(item => {
          const versions = getVersions(item.workflows);
          const versionsCount = item.workflows.length;
          const name = item.workflowName || item.bpmnProcessId;
          const Statistic = (
            <Styled.IncidentStatistic
              label={`${name} (Version ${versions})`}
              incidentsCount={item.instancesWithActiveIncidentsCount}
              activeCount={item.activeInstancesCount}
            />
          );

          return (
            <Styled.Li key={item.bpmnProcessId}>
              {versionsCount === 1 ? (
                Statistic
              ) : (
                <Collapse
                  content={this.getVersionStatistics(item.workflows)}
                  header={Statistic}
                  buttonTitle={`Expand ${versionsCount} Instances with Incidents of Workflow ${name} `}
                />
              )}
            </Styled.Li>
          );
        })}
      </ul>
    );
  }
}
