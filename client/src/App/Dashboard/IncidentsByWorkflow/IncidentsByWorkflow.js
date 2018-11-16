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

  renderVersionStatistics = items => {
    return (
      <ul>
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
      </ul>
    );
  };

  renderIncidentStatistic = (item, name) => {
    const versions = getVersions(item.workflows);
    return (
      <Styled.IncidentStatistic
        label={`${name} (Version ${versions})`}
        incidentsCount={item.instancesWithActiveIncidentsCount}
        activeCount={item.activeInstancesCount}
      />
    );
  };

  render() {
    const {incidents} = this.props;
    return (
      <ul>
        {incidents.map((item, index) => {
          const versionsCount = item.workflows.length;
          const name = item.workflowName || item.bpmnProcessId;
          const IncidentStatistic = this.renderIncidentStatistic(item, name);
          return (
            <Styled.Li
              key={item.bpmnProcessId}
              data-test={`incident-byWorkflow-${index}`}
            >
              {versionsCount === 1 ? (
                IncidentStatistic
              ) : (
                <Collapse
                  content={this.renderVersionStatistics(item.workflows)}
                  header={IncidentStatistic}
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
