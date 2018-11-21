import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';
import Collapse from './Collapse';
import IncidentStatistic from './IncidentStatistic';
import {getUrl, getTitle} from './service';

function getVersions(workflows = []) {
  return workflows.map(item => item.version).join(', ');
}

export default class IncidentsByWorkflow extends React.Component {
  static propTypes = {
    incidents: PropTypes.array
  };

  renderVersionStatistics = (workflowName, bpmnProcessId, items) => {
    return (
      <ul>
        {items.map(item => {
          return (
            <Styled.VersionLi key={item.workflowId}>
              <Styled.IncidentLink
                to={getUrl(bpmnProcessId, item.version)}
                title={getTitle(
                  workflowName,
                  item.version,
                  item.instancesWithActiveIncidentsCount
                )}
              >
                <IncidentStatistic
                  label={`Version ${item.version}`}
                  incidentsCount={item.instancesWithActiveIncidentsCount}
                  activeCount={item.activeInstancesCount}
                  perUnit
                />
              </Styled.IncidentLink>
            </Styled.VersionLi>
          );
        })}
      </ul>
    );
  };

  renderIncidentStatistic = (item, name) => {
    const versions = getVersions(item.workflows);
    return (
      <Styled.IncidentLink
        to={getUrl(item.bpmnProcessId, versions)}
        title={getTitle(
          item.workflowName || item.bpmnProcessId,
          versions,
          item.instancesWithActiveIncidentsCount
        )}
      >
        <IncidentStatistic
          label={`${name} (Version ${versions})`}
          incidentsCount={item.instancesWithActiveIncidentsCount}
          activeCount={item.activeInstancesCount}
        />
      </Styled.IncidentLink>
    );
  };

  render() {
    const {incidents} = this.props;
    return (
      <ul>
        {incidents.map((item, index) => {
          const versionsCount = item.workflows.length;
          const name = item.workflowName || item.bpmnProcessId;
          const IncidentStatisticComponent = this.renderIncidentStatistic(
            item,
            name
          );
          return (
            <Styled.Li
              key={item.bpmnProcessId}
              data-test={`incident-byWorkflow-${index}`}
            >
              {versionsCount === 1 ? (
                IncidentStatisticComponent
              ) : (
                <Collapse
                  content={this.renderVersionStatistics(
                    name,
                    item.bpmnProcessId,
                    item.workflows
                  )}
                  header={IncidentStatisticComponent}
                  buttonTitle={`Expand ${
                    item.instancesWithActiveIncidentsCount
                  } Instances with Incidents of Workflow ${name}`}
                />
              )}
            </Styled.Li>
          );
        })}
      </ul>
    );
  }
}
