/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAccordionItemTitle} from './utils/getAccordionItemTitle';
import {getAccordionItemLabel} from './utils/getAccordionItemLabel';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {Li, LinkWrapper, ErrorText} from '../styled';
import {InstancesBar} from 'modules/components/InstancesBar';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';
import {useProcessDefinitionVersionStatistics} from 'modules/queries/processDefinitionStatistics/useProcessDefinitionVersionStatistics';
import {InlineLoading} from '@carbon/react';
import type {ProcessDefinitionInstanceVersionStatistics} from '@camunda/camunda-api-zod-schemas/8.8';
import {DEFAULT_TENANT} from 'modules/constants';

type Props = {
  processDefinitionId: string;
  processName: string;
  tenantId: string;
  tabIndex?: number;
};

const Details: React.FC<Props> = ({
  processDefinitionId,
  processName,
  tenantId,
  tabIndex,
}) => {
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;
  const tenantsById = useAvailableTenants();

  const result = useProcessDefinitionVersionStatistics(processDefinitionId, {
    payload:
      isMultiTenancyEnabled && tenantId !== DEFAULT_TENANT
        ? {
            sort: [
              {field: 'activeInstancesWithIncidentCount', order: 'desc'},
              {field: 'activeInstancesWithoutIncidentCount', order: 'desc'},
            ],
            filter: {
              processDefinitionId,
              tenantId,
            },
          }
        : {
            sort: [
              {field: 'activeInstancesWithIncidentCount', order: 'desc'},
              {field: 'activeInstancesWithoutIncidentCount', order: 'desc'},
            ],
            filter: {
              processDefinitionId,
            },
          },
  });

  if (result.status === 'pending' && !result.data) {
    return (
      <ul>
        <Li>
          <InlineLoading description="Loading versions..." />
        </Li>
      </ul>
    );
  }

  if (result.status === 'error' || !result.data) {
    return (
      <ul>
        <Li>
          <ErrorText>Failed to load version details</ErrorText>
        </Li>
      </ul>
    );
  }

  return (
    <ul>
      {result.data.items.map(
        (versionItem: ProcessDefinitionInstanceVersionStatistics) => {
          const {
            processDefinitionId,
            processDefinitionKey,
            processDefinitionName,
            processDefinitionVersion,
            activeInstancesWithIncidentCount,
            activeInstancesWithoutIncidentCount,
            tenantId: itemTenantId,
          } = versionItem;

          const normalizedTenantId = itemTenantId ?? DEFAULT_TENANT;
          const tenantName =
            tenantsById[normalizedTenantId] ?? normalizedTenantId;

          const totalInstancesCount =
            activeInstancesWithIncidentCount +
            activeInstancesWithoutIncidentCount;

          return (
            <Li key={processDefinitionKey}>
              <LinkWrapper
                tabIndex={tabIndex ?? 0}
                to={Locations.processes({
                  process: processDefinitionId,
                  version: processDefinitionVersion.toString(),
                  active: true,
                  incidents: true,
                  ...(totalInstancesCount === 0
                    ? {
                        completed: true,
                        canceled: true,
                      }
                    : {}),
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
                    link: 'dashboard-process-instances-by-name-single-version',
                  });
                }}
                title={getAccordionItemTitle({
                  processName: processDefinitionName || processName,
                  instancesCount: totalInstancesCount,
                  version: processDefinitionVersion,
                  ...(isMultiTenancyEnabled
                    ? {
                        tenant: tenantName,
                      }
                    : {}),
                })}
              >
                <InstancesBar
                  label={{
                    type: 'process',
                    size: 'small',
                    text: getAccordionItemLabel({
                      name: processDefinitionName || processName,
                      instancesCount: totalInstancesCount,
                      version: processDefinitionVersion,
                      ...(isMultiTenancyEnabled
                        ? {
                            tenant: tenantName,
                          }
                        : {}),
                    }),
                  }}
                  incidentsCount={activeInstancesWithIncidentCount}
                  activeInstancesCount={activeInstancesWithoutIncidentCount}
                  size="small"
                />
              </LinkWrapper>
            </Li>
          );
        },
      )}
    </ul>
  );
};

export {Details};
