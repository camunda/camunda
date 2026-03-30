/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import isNil from 'lodash/isNil';
import {formatDate} from 'modules/utils/date';
import {ProcessInstanceOperations} from './ProcessInstanceOperations';
import {getProcessDefinitionName} from 'modules/utils/instance';
import {Link} from 'modules/components/Link';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {InstanceHeader} from 'modules/components/InstanceHeader';
import {Skeleton} from 'modules/components/InstanceHeader/Skeleton';
import {VersionTag} from './styled';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {useProcessInstanceIncidentsCount} from 'modules/queries/incidents/useProcessInstanceIncidentsCount';
import {hasCalledProcessInstances} from 'modules/bpmn-js/utils/hasCalledProcessInstances';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {
  isWidthBelowBreakpoint,
  useMatchMedia,
} from 'modules/hooks/useMatchMedia';

const headerColumns = [
  'Process Instance Key',
  'Version',
  'Version Tag',
  'Tenant',
  'Start Date',
  'End Date',
  'Called Instances',
] as const;

const skeletonColumns: {
  name: (typeof headerColumns)[number];
  skeletonWidth: string;
}[] = [
  {
    name: 'Process Instance Key',
    skeletonWidth: '136px',
  },
  {
    name: 'Version',
    skeletonWidth: '34px',
  },
  {
    name: 'Start Date',
    skeletonWidth: '142px',
  },
  {
    name: 'Called Instances',
    skeletonWidth: '142px',
  },
] as const;

type Props = {
  processInstance: ProcessInstance;
};

const ProcessInstanceHeader: React.FC<Props> = ({processInstance}) => {
  const {
    processInstanceKey,
    processDefinitionVersion,
    processDefinitionVersionTag,
    tenantId,
    startDate,
    endDate,
    state,
    hasIncident,
    processDefinitionId,
  } = processInstance;
  const showReducedContent = useMatchMedia(isWidthBelowBreakpoint('lg'));

  const tenantsById = useAvailableTenants();
  const tenantName = tenantsById[tenantId] ?? tenantId;
  const isMultiTenancyEnabled = getClientConfig().multiTenancyEnabled;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {isPending, data: processInstanceXmlData} = useProcessInstanceXml({
    processDefinitionKey,
  });
  const {data: incidentsCount = 0} = useProcessInstanceIncidentsCount(
    processInstanceKey,
    {
      enabled: hasIncident,
    },
  );

  if (processInstance === null || isPending) {
    return <Skeleton headerColumns={skeletonColumns} />;
  }

  const versionColumnTitle = `View process "${getProcessDefinitionName(
    processInstance,
  )} version ${processDefinitionVersion}" instances${
    isMultiTenancyEnabled ? ` - ${tenantName}` : ''
  }`;
  const hasVersionTag = !isNil(processDefinitionVersionTag);
  const processInstanceState = hasIncident ? 'INCIDENT' : state;

  return (
    <InstanceHeader
      state={processInstanceState}
      instanceName={getProcessDefinitionName(processInstance)}
      incidentsCount={hasIncident ? incidentsCount : 0}
      headerColumns={headerColumns.filter((name) => {
        if (name === 'Tenant') {
          return isMultiTenancyEnabled;
        }
        if (name === 'Version Tag') {
          return hasVersionTag;
        }
        if (name === 'End Date') {
          return endDate !== null && !showReducedContent;
        }
        if (name === 'Start Date' || name === 'Called Instances') {
          return !showReducedContent;
        }
        return true;
      })}
      bodyColumns={[
        {title: processInstanceKey, content: processInstanceKey},
        {
          hideOverflowingContent: false,
          content: (
            <Link
              to={Locations.processes({
                processDefinitionVersion: processDefinitionVersion?.toString(),
                processDefinitionId,
                active: true,
                incidents: true,
                ...(isMultiTenancyEnabled
                  ? {
                      tenantId,
                    }
                  : {}),
              })}
              title={versionColumnTitle}
              aria-label={versionColumnTitle}
              onClick={() => {
                tracking.track({
                  eventName: 'navigation',
                  link: 'process-details-version',
                });
              }}
            >
              {processDefinitionVersion}
            </Link>
          ),
        },
        {
          hidden: !hasVersionTag,
          title: 'User-defined label identifying a definition.',
          content: (
            <VersionTag size="sm" type="outline">
              {processDefinitionVersionTag}
            </VersionTag>
          ),
        },
        {
          hidden: !isMultiTenancyEnabled,
          title: tenantName,
          content: tenantName,
        },
        {
          hidden: showReducedContent,
          title: formatDate(startDate),
          content: formatDate(startDate),
          dataTestId: 'start-date',
        },
        {
          hidden: endDate === null || showReducedContent,
          title: formatDate(endDate),
          content: formatDate(endDate),
          dataTestId: 'end-date',
        },
        {
          hidden: showReducedContent,
          hideOverflowingContent: false,
          content: (
            <>
              {hasCalledProcessInstances(
                Object.values(processInstanceXmlData?.businessObjects ?? {}),
              ) ? (
                <Link
                  to={Locations.processes({
                    parentProcessInstanceKey: processInstanceKey,
                    active: true,
                    incidents: true,
                    canceled: true,
                    completed: true,
                  })}
                  onClick={() => {
                    panelStatesStore.expandFiltersPanel();
                    tracking.track({
                      eventName: 'navigation',
                      link: 'process-details-called-instances',
                    });
                  }}
                  title="View all called instances"
                  aria-label="View all called instances"
                >
                  View All
                </Link>
              ) : (
                'None'
              )}
            </>
          ),
        },
      ]}
      additionalContent={
        <ProcessInstanceOperations
          isCollapsed={showReducedContent}
          processInstance={processInstance}
        />
      }
    />
  );
};

export {ProcessInstanceHeader};
