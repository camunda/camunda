/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Collapse from '../Collapse';
import InstancesBar from 'modules/components/InstancesBar';

import * as Styled from './styled';
import {
  concatUrl,
  concatTitle,
  concatGroupTitle,
  concatLabel,
  concatGroupLabel,
  concatButtonTitle
} from './service';

export default class InstancesByWorkflow extends React.Component {
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
      <Styled.VersionList>
        {items.map(item => {
          const totalInstancesCount =
            item.instancesWithActiveIncidentsCount + item.activeInstancesCount;

          return (
            <Styled.VersionLi key={item.workflowId}>
              <Styled.IncidentLink
                to={concatUrl({
                  bpmnProcessId: item.bpmnProcessId,
                  versions: [item],
                  hasFinishedInstances: totalInstancesCount === 0
                })}
                title={concatTitle(
                  item.name || workflowName,
                  totalInstancesCount,
                  item.version
                )}
                boxSize="small"
              >
                <InstancesBar
                  label={concatLabel(
                    item.name || workflowName,
                    totalInstancesCount,
                    item.version
                  )}
                  incidentsCount={item.instancesWithActiveIncidentsCount}
                  activeCount={item.activeInstancesCount}
                  size="small"
                />
              </Styled.IncidentLink>
            </Styled.VersionLi>
          );
        })}
      </Styled.VersionList>
    );
  };

  renderIncidentByWorkflow = item => {
    const name = item.workflowName || item.bpmnProcessId;
    const totalInstancesCount =
      item.instancesWithActiveIncidentsCount + item.activeInstancesCount;

    return (
      <Styled.IncidentLink
        to={concatUrl({
          bpmnProcessId: item.bpmnProcessId,
          versions: item.workflows,
          hasFinishedInstances: totalInstancesCount === 0
        })}
        title={concatGroupTitle(
          name,
          totalInstancesCount,
          item.workflows.length
        )}
      >
        <InstancesBar
          label={concatGroupLabel(
            name,
            totalInstancesCount,
            item.workflows.length
          )}
          incidentsCount={item.instancesWithActiveIncidentsCount}
          activeCount={item.activeInstancesCount}
          size="medium"
        />
      </Styled.IncidentLink>
    );
  };

  render() {
    return (
      <ul>
        {this.props.incidents.map((item, index) => {
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
                  buttonTitle={concatButtonTitle(name, totalInstancesCount)}
                />
              )}
            </Styled.Li>
          );
        })}
      </ul>
    );
  }
}
