/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Collapse from '../Collapse';
import {getFilterQueryString} from 'modules/utils/filter';
import PanelListItem from '../PanelListItem';

import * as Styled from './styled';

export default class IncidentsByError extends React.Component {
  static propTypes = {
    incidents: PropTypes.arrayOf(
      PropTypes.shape({
        errorMessage: PropTypes.string.isRequired,
        instancesWithErrorCount: PropTypes.number.isRequired,
        workflows: PropTypes.arrayOf(
          PropTypes.shape({
            bpmnProcessId: PropTypes.string.isRequired,
            errorMessage: PropTypes.string.isRequired,
            instancesWithActiveIncidentsCount: PropTypes.number.isRequired,
            name: PropTypes.string,
            version: PropTypes.number.isRequired,
            workflowId: PropTypes.string.isRequired
          })
        ).isRequired
      })
    )
  };

  renderIncidentsPerWorkflow = (errorMessage, items) => {
    return (
      <Styled.VersionUl>
        {items.map(item => {
          const name = item.name || item.bpmnProcessId;
          const query = getFilterQueryString({
            workflow: item.bpmnProcessId,
            version: `${item.version}`,
            errorMessage,
            incidents: true
          });
          const title = `View ${
            item.instancesWithActiveIncidentsCount
          } Instances with error ${item.errorMessage} in version ${
            item.version
          } of Workflow ${name}`;

          return (
            <Styled.VersionLi key={item.workflowId}>
              <PanelListItem
                to={`/instances${query}`}
                title={title}
                boxSize="small"
              >
                <Styled.VersionLiInstancesBar
                  label={`${name} – Version ${item.version}`}
                  incidentsCount={item.instancesWithActiveIncidentsCount}
                  barHeight={2}
                  size="small"
                />
              </PanelListItem>
            </Styled.VersionLi>
          );
        })}
      </Styled.VersionUl>
    );
  };

  renderIncidentByError = (errorMessage, instancesWithErrorCount) => {
    const query = getFilterQueryString({
      errorMessage: errorMessage,
      incidents: true
    });

    const title = `View ${instancesWithErrorCount} Instances with error ${errorMessage}`;

    return (
      <PanelListItem to={`/instances${query}`} title={title}>
        <Styled.LiInstancesBar
          label={`${errorMessage}`}
          incidentsCount={instancesWithErrorCount}
          size="medium"
          barHeight={2}
        />
      </PanelListItem>
    );
  };

  render() {
    return (
      <ul>
        {this.props.incidents.map((item, index) => {
          const buttonTitle = `Expand ${
            item.instancesWithErrorCount
          } Instances with error "${item.errorMessage}"`;

          return (
            <Styled.Li
              key={item.errorMessage}
              data-test={`incident-byError-${index}`}
            >
              <Collapse
                content={this.renderIncidentsPerWorkflow(
                  item.errorMessage,
                  item.workflows
                )}
                header={this.renderIncidentByError(
                  item.errorMessage,
                  item.instancesWithErrorCount
                )}
                buttonTitle={buttonTitle}
              />
            </Styled.Li>
          );
        })}
      </ul>
    );
  }
}
