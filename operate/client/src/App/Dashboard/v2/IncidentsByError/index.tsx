/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {PartiallyExpandableDataTable} from '../PartiallyExpandableDataTable';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {getAccordionTitle} from './utils/getAccordionTitle';
import {InstancesBar} from 'modules/components/InstancesBar';
import {truncateErrorMessage} from './utils/truncateErrorMessage';
import {Skeleton} from '../PartiallyExpandableDataTable/Skeleton';
import {LinkWrapper, ErrorMessage} from '../../styled';
import {EmptyState} from 'modules/components/EmptyState';
import EmptyStateProcessIncidents from 'modules/components/Icon/empty-state-process-incidents.svg?react';
import {useIncidentProcessInstanceStatisticsByError} from 'modules/queries/incidentStatistics/useIncidentProcessInstanceStatisticsByError';
import type {IncidentProcessInstanceStatisticsByError} from '@camunda/camunda-api-zod-schemas/8.9';
import {Details} from './Details';

const IncidentsByError: React.FC = () => {
  const result = useIncidentProcessInstanceStatisticsByError();
  const incidents = result.data?.items ?? [];

  const expandedContents = incidents.reduce<
    Record<string, React.ReactElement<{tabIndex: number}>>
  >((accumulator, item) => {
    accumulator[String(item.errorHashCode)] = (
      <Details
        errorMessage={item.errorMessage}
        incidentErrorHashCode={item.errorHashCode}
      />
    );
    return accumulator;
  }, {});

  if (result.status === 'pending' && !result.data) {
    return <Skeleton />;
  }

  if (result.data?.items.length === 0) {
    return (
      <EmptyState
        icon={<EmptyStateProcessIncidents title="Your processes are healthy" />}
        heading="Your processes are healthy"
        description="There are no incidents on any instances."
      />
    );
  }

  if (result.status === 'error') {
    return <ErrorMessage />;
  }

  return (
    <PartiallyExpandableDataTable
      dataTestId="incident-byError"
      headers={[{key: 'incident', header: 'incident'}]}
      expandedContents={expandedContents}
      rows={incidents.map((item: IncidentProcessInstanceStatisticsByError) => {
        const {errorHashCode, errorMessage, activeInstancesWithErrorCount} =
          item;

        return {
          id: String(errorHashCode),
          incident: (
            <LinkWrapper
              to={Locations.processes({
                errorMessage: truncateErrorMessage(errorMessage),
                incidentErrorHashCode: errorHashCode,
                incidents: true,
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-incidents-by-error-message-all-processes',
                });
              }}
              title={getAccordionTitle(
                activeInstancesWithErrorCount,
                errorMessage,
              )}
            >
              <InstancesBar
                label={{type: 'incident', size: 'small', text: errorMessage}}
                incidentsCount={activeInstancesWithErrorCount}
                size="medium"
              />
            </LinkWrapper>
          ),
        };
      })}
    />
  );
};

export {IncidentsByError};
