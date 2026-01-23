/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InlineLoading} from '@carbon/react';
import {getAccordionItemTitle} from './utils/getAccordionItemTitle';
import {getAccordionItemLabel} from './utils/getAccordionItemLabel';
import {truncateErrorMessage} from './utils/truncateErrorMessage';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {Li, LinkWrapper, ErrorText} from '../../styled';
import {InstancesBar} from 'modules/components/InstancesBar';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';
import {useIncidentProcessInstanceStatisticsByDefinition} from 'modules/queries/incidentStatistics/useIncidentProcessInstanceStatisticsByDefinition';
import {DEFAULT_TENANT} from 'modules/constants';

type Props = {
  errorMessage: string;
  incidentErrorHashCode: number;
  tabIndex?: number;
};

const Details: React.FC<Props> = ({
  errorMessage,
  incidentErrorHashCode,
  tabIndex,
}) => {
  const tenantsById = useAvailableTenants();
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;

  const result = useIncidentProcessInstanceStatisticsByDefinition({
    payload: {
      filter: {
        errorHashCode: incidentErrorHashCode,
      },
    },
  });

  if (result.status === 'pending' && !result.data) {
    return (
      <ul>
        <Li>
          <InlineLoading description="Loading incident details..." />
        </Li>
      </ul>
    );
  }

  if (result.status === 'error' || result.data === undefined) {
    return (
      <ul>
        <Li>
          <ErrorText>Failed to load incident details</ErrorText>
        </Li>
      </ul>
    );
  }

  if (result.data.items.length === 0) {
    return (
      <ul>
        <Li>
          <ErrorText>No processes found for this error</ErrorText>
        </Li>
      </ul>
    );
  }

  return (
    <ul>
      {result.data.items.map((item) => {
        const {
          processDefinitionId,
          processDefinitionKey,
          processDefinitionName,
          processDefinitionVersion,
          tenantId,
          activeInstancesWithErrorCount,
        } = item;

        const normalizedTenantId = tenantId ?? DEFAULT_TENANT;
        const tenantName =
          tenantsById[normalizedTenantId] ?? normalizedTenantId;
        const name = processDefinitionName || processDefinitionId;

        return (
          <Li key={`${processDefinitionKey}-${normalizedTenantId}`}>
            <LinkWrapper
              tabIndex={tabIndex ?? 0}
              to={Locations.processes({
                process: processDefinitionId,
                version: processDefinitionVersion.toString(),
                errorMessage: truncateErrorMessage(errorMessage),
                incidentErrorHashCode,
                incidents: true,
                ...(isMultiTenancyEnabled
                  ? {
                      tenant: normalizedTenantId,
                    }
                  : {}),
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-incidents-by-error-message-single-version',
                });
              }}
              title={getAccordionItemTitle({
                processName: name,
                instancesCount: activeInstancesWithErrorCount,
                versionName: processDefinitionVersion,
                errorMessage,
                ...(isMultiTenancyEnabled
                  ? {
                      tenant: tenantName,
                    }
                  : {}),
              })}
            >
              <InstancesBar
                label={{
                  type: 'incident',
                  size: 'small',
                  text: getAccordionItemLabel({
                    name,
                    version: processDefinitionVersion,
                    ...(isMultiTenancyEnabled
                      ? {
                          tenant: tenantName,
                        }
                      : {}),
                  }),
                }}
                incidentsCount={activeInstancesWithErrorCount}
                size="small"
              />
            </LinkWrapper>
          </Li>
        );
      })}
    </ul>
  );
};

export {Details};
