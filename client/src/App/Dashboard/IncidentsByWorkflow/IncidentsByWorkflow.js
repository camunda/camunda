/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Collapse from '../Collapse';
import IncidentByWorkflow from './IncidentByWorkflow';

import * as Styled from './styled';
import {getUrl, getTitle} from './service';

function getVersions(workflows = []) {
  return workflows.map(item => item.version).join(', ');
}

export default class IncidentsByWorkflow extends React.Component {
  static propTypes = {
    incidents: PropTypes.arrayOf(
      PropTypes.shape({
        activeInstancesCount: PropTypes.number.isRequired,
        bpmnProcessId: PropTypes.string.isRequired,
        instancesWithActiveIncidentsCount: PropTypes.number.isRequired,
        workflowName: PropTypes.string,
        workflows: PropTypes.arrayOf(
          PropTypes.shape({
            activeInstancesCount: PropTypes.number.isRequired,
            bpmnProcessId: PropTypes.string.isRequired,
            instancesWithActiveIncidentsCount: PropTypes.number.isRequired,
            name: PropTypes.string,
            version: PropTypes.number.isRequired,
            workflowId: PropTypes.string.isRequired
          })
        ).isRequired
      })
    )
  };

  renderIncidentsPerVersion = (workflowName, items) => {
    return (
      <ul>
        {items.map(item => {
          return (
            <Styled.VersionLi key={item.workflowId}>
              <Styled.IncidentLink
                to={getUrl(item.bpmnProcessId, item.version)}
                title={getTitle(
                  workflowName,
                  item.version,
                  item.instancesWithActiveIncidentsCount
                )}
              >
                <IncidentByWorkflow
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

  renderIncidentByWorkflow = item => {
    const versions = getVersions(item.workflows);
    const name = item.workflowName || item.bpmnProcessId;

    return (
      <Styled.IncidentLink
        to={getUrl(item.bpmnProcessId, versions)}
        title={getTitle(name, versions, item.instancesWithActiveIncidentsCount)}
      >
        <IncidentByWorkflow
          label={`${name} – Version ${versions}`}
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
          const workflowsCount = item.workflows.length;
          const name = item.workflowName || item.bpmnProcessId;
          const IncidentByWorkflowComponent = this.renderIncidentByWorkflow(
            item
          );
          return (
            <Styled.Li
              key={item.bpmnProcessId}
              data-test={`incident-byWorkflow-${index}`}
            >
              {workflowsCount === 1 ? (
                IncidentByWorkflowComponent
              ) : (
                <Collapse
                  content={this.renderIncidentsPerVersion(name, item.workflows)}
                  header={IncidentByWorkflowComponent}
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
