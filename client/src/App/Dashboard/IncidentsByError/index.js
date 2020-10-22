/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Collapse} from '../Collapse';
import {getFilterQueryString} from 'modules/utils/filter';
import PanelListItem from '../PanelListItem';
import {
  concatTitle,
  concatLabel,
  concatGroupTitle,
  concatButtonTitle,
} from './service';

import * as Styled from './styled';
import {incidentsByError} from 'modules/stores/incidentsByError';

import {INCIDENTS_BY_ERROR} from '../constants';
import {Skeleton} from '../Skeleton';
import {observer} from 'mobx-react';

const IncidentsByError = observer(
  class IncidentsByError extends React.Component {
    componentDidMount = () => {
      incidentsByError.getIncidentsByError();
    };

    componentWillUnmount = () => {
      incidentsByError.reset();
    };

    renderIncidentsPerWorkflow = (errorMessage, items) => {
      return (
        <Styled.VersionUl>
          {items.map((item) => {
            const name = item.name || item.bpmnProcessId;
            const query = getFilterQueryString({
              workflow: item.bpmnProcessId,
              version: `${item.version}`,
              errorMessage: encodeURIComponent(errorMessage),
              incidents: true,
            });
            const title = concatTitle(
              name,
              item.instancesWithActiveIncidentsCount,
              item.version,
              errorMessage
            );
            const label = concatLabel(name, item.version);
            return (
              <Styled.VersionLi key={item.workflowId}>
                <PanelListItem
                  to={`/instances${query}`}
                  title={title}
                  $boxSize="small"
                >
                  <Styled.VersionLiInstancesBar
                    label={label}
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
        errorMessage: encodeURIComponent(errorMessage),
        incidents: true,
      });

      const title = concatGroupTitle(instancesWithErrorCount, errorMessage);

      return (
        <PanelListItem to={`/instances${query}`} title={title}>
          <Styled.LiInstancesBar
            label={errorMessage}
            incidentsCount={instancesWithErrorCount}
            size="medium"
            barHeight={2}
          />
        </PanelListItem>
      );
    };

    render() {
      const {
        state: {incidents, isFailed, isLoaded},
        isDataAvailable,
      } = incidentsByError;

      if (!isDataAvailable) {
        return (
          <Skeleton
            data={incidents}
            isFailed={isFailed}
            isLoaded={isLoaded}
            errorType={INCIDENTS_BY_ERROR}
          />
        );
      }

      return (
        <ul data-testid="incidents-by-error">
          {incidents.map((item, index) => {
            const buttonTitle = concatButtonTitle(
              item.instancesWithErrorCount,
              item.errorMessage
            );

            return (
              <Styled.Li
                key={item.errorMessage}
                data-testid={`incident-byError-${index}`}
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
);

export {IncidentsByError};
