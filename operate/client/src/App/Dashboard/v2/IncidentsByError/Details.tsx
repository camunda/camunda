/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAccordionItemTitle} from './utils/getAccordionItemTitle';
import {getAccordionItemLabel} from './utils/getAccordionItemLabel';
import {truncateErrorMessage} from './utils/truncateErrorMessage';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import type {ProcessDto} from 'modules/api/incidents/fetchIncidentsByError';
import {Li, LinkWrapper} from '../../styled';
import {InstancesBar} from 'modules/components/InstancesBar';
import {observer} from 'mobx-react';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';

type Props = {
  errorMessage: string;
  incidentErrorHashCode: number;
  processes: ProcessDto[];
  tabIndex?: number;
};

const Details: React.FC<Props> = observer(
  ({errorMessage, incidentErrorHashCode, processes, tabIndex}) => {
    const tenantsById = useAvailableTenants();
    const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;

    return (
      <ul>
        {processes.map((item) => {
          const name = item.name || item.bpmnProcessId;

          const tenantName = tenantsById[item.tenantId] ?? item.tenantId;

          return (
            <Li key={item.processId}>
              <LinkWrapper
                tabIndex={tabIndex ?? 0}
                to={Locations.processes({
                  process: item.bpmnProcessId,
                  version: item.version.toString(),
                  errorMessage: truncateErrorMessage(errorMessage),
                  incidentErrorHashCode,
                  incidents: true,
                  ...(isMultiTenancyEnabled
                    ? {
                        tenant: item.tenantId,
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
                  instancesCount: item.instancesWithActiveIncidentsCount,
                  versionName: item.version,
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
                      version: item.version,
                      ...(isMultiTenancyEnabled
                        ? {
                            tenant: tenantName,
                          }
                        : {}),
                    }),
                  }}
                  incidentsCount={item.instancesWithActiveIncidentsCount}
                  size="small"
                />
              </LinkWrapper>
            </Li>
          );
        })}
      </ul>
    );
  },
);

export {Details};
