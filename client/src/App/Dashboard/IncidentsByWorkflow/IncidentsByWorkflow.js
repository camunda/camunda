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
import {
  getUrl,
  getTitle,
  getGroupTitle,
  getLabel,
  getGroupLabel,
  getButtonTitle
} from './service';

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
          const totalInstancesCount =
            item.instancesWithActiveIncidentsCount + item.activeInstancesCount;

          return (
            <Styled.VersionLi key={item.workflowId}>
              <Styled.IncidentLink
                to={getUrl(item.bpmnProcessId, item.version)}
                title={getTitle(
                  workflowName,
                  totalInstancesCount,
                  item.version
                )}
              >
                <IncidentByWorkflow
                  label={getLabel(
                    workflowName,
                    totalInstancesCount,
                    item.version
                  )}
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
    const totalInstancesCount =
      item.instancesWithActiveIncidentsCount + item.activeInstancesCount;

    return (
      <Styled.IncidentLink
        to={getUrl(item.bpmnProcessId, versions)}
        title={getGroupTitle(name, totalInstancesCount, item.workflows.length)}
      >
        <IncidentByWorkflow
          label={getGroupLabel(
            name,
            totalInstancesCount,
            item.workflows.length
          )}
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
          const totalInstancesCount =
            item.instancesWithActiveIncidentsCount + item.activeInstancesCount;

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
                  buttonTitle={getButtonTitle(name, totalInstancesCount)}
                />
              )}
            </Styled.Li>
          );
        })}
      </ul>
    );
  }
}
