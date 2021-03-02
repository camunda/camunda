/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
import {Collapse} from '../Collapse';
import {getFilterQueryString} from 'modules/utils/filter';
import {PanelListItem} from '../PanelListItem';
import {
  concatTitle,
  concatLabel,
  concatGroupTitle,
  concatButtonTitle,
} from './service';

import * as Styled from './styled';
import {incidentsByErrorStore} from 'modules/stores/incidentsByError';
import {StatusMessage} from 'modules/components/StatusMessage';
import {Skeleton} from '../Skeleton';
import {observer} from 'mobx-react';
import {IS_FILTERS_V2} from 'modules/utils/filter';
import {Locations} from 'modules/routes';
import {mergeQueryParams} from 'modules/utils/mergeQueryParams';
import {getPersistentQueryParams} from 'modules/utils/getPersistentQueryParams';

const IncidentsByError = observer(() => {
  useEffect(() => {
    incidentsByErrorStore.getIncidentsByError();
    return () => {
      incidentsByErrorStore.reset();
    };
  }, []);

  const renderIncidentsPerWorkflow = (errorMessage: any, items: any) => {
    return (
      <Styled.VersionUl>
        {items.map((item: any) => {
          const name = item.name || item.bpmnProcessId;
          const queryParams = getFilterQueryString({
            workflow: item.bpmnProcessId,
            version: `${item.version}`,
            errorMessage,
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
              {IS_FILTERS_V2 ? (
                <PanelListItem
                  to={(location) =>
                    Locations.filters(location, {
                      workflow: item.bpmnProcessId,
                      workflowVersion: item.version,
                      errorMessage,
                      incidents: true,
                    })
                  }
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
              ) : (
                <PanelListItem
                  to={(location) => ({
                    ...location,
                    pathname: '/instances',
                    search: mergeQueryParams({
                      newParams: queryParams,
                      prevParams: getPersistentQueryParams(location.search),
                    }),
                  })}
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
              )}
            </Styled.VersionLi>
          );
        })}
      </Styled.VersionUl>
    );
  };

  const renderIncidentByError = (
    errorMessage: any,
    instancesWithErrorCount: any
  ) => {
    const queryParams = getFilterQueryString({
      errorMessage,
      incidents: true,
    });

    const title = concatGroupTitle(instancesWithErrorCount, errorMessage);
    if (IS_FILTERS_V2) {
      return (
        <PanelListItem
          to={(location) =>
            Locations.filters(location, {
              errorMessage,
              incidents: true,
            })
          }
          title={title}
        >
          <Styled.LiInstancesBar
            label={errorMessage}
            incidentsCount={instancesWithErrorCount}
            size="medium"
            barHeight={2}
          />
        </PanelListItem>
      );
    }

    return (
      <PanelListItem
        to={(location) => ({
          ...location,
          pathname: '/instances',
          search: mergeQueryParams({
            newParams: queryParams,
            prevParams: getPersistentQueryParams(location.search),
          }),
        })}
        title={title}
      >
        <Styled.LiInstancesBar
          label={errorMessage}
          incidentsCount={instancesWithErrorCount}
          size="medium"
          barHeight={2}
        />
      </PanelListItem>
    );
  };

  const {incidents, status} = incidentsByErrorStore.state;

  if (['initial', 'fetching'].includes(status)) {
    return <Skeleton />;
  }

  if (status === 'fetched' && incidents.length === 0) {
    return (
      <StatusMessage variant="success">
        There are no Instances with Incidents
      </StatusMessage>
    );
  }

  if (status === 'error') {
    return (
      <StatusMessage variant="error">
        Incidents by Error Message could not be fetched
      </StatusMessage>
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
              content={renderIncidentsPerWorkflow(
                item.errorMessage,
                item.workflows
              )}
              header={renderIncidentByError(
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
});

export {IncidentsByError};
