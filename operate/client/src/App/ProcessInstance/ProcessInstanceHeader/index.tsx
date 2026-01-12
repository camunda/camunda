/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import isNil from 'lodash/isNil';
import {formatDate} from 'modules/utils/date';
import {ProcessInstanceOperations} from './ProcessInstanceOperations';
import {getProcessDefinitionName} from 'modules/utils/instance';
import {Link} from 'modules/components/Link';
import {Locations, Paths} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {InstanceHeader} from 'modules/components/InstanceHeader';
import {Skeleton} from 'modules/components/InstanceHeader/Skeleton';
import {
  VersionTag,
  ProcessNameContainer,
  ProcessNameLabel,
  IncidentCount,
  HeaderContent,
} from './styled';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {hasCalledProcessInstances} from 'modules/bpmn-js/utils/hasCalledProcessInstances';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';
import {useProcessInstanceIncidentsCount} from 'modules/queries/incidents/useProcessInstanceIncidentsCount';
import {useNavigate} from 'react-router-dom';
import {Button} from '@carbon/react';
import {ArrowLeft} from '@carbon/react/icons';
import {styles} from '@carbon/elements';
import {BREAKPOINTS} from 'modules/constants';

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
    name: 'End Date',
    skeletonWidth: '142px',
  },
  {
    name: 'Called Instances',
    skeletonWidth: '142px',
  },
] as const;

type Props = {
  processInstance: ProcessInstance;
  isBreadcrumbVisible?: boolean;
};

const ProcessInstanceHeader: React.FC<Props> = ({processInstance, isBreadcrumbVisible = false}) => {
  const {
    processInstanceKey,
    processDefinitionVersion,
    processDefinitionVersionTag,
    tenantId,
    startDate,
    endDate,
    parentProcessInstanceKey,
    state,
    hasIncident,
    processDefinitionId,
  } = processInstance;
  const tenantsById = useAvailableTenants();
  const tenantName = tenantsById[tenantId] ?? tenantId;
  const navigate = useNavigate();

  const [isMedium, setIsMedium] = useState(
    window.innerWidth >= BREAKPOINTS.md && window.innerWidth < BREAKPOINTS.lg,
  );

  useEffect(() => {
    const updateBreakpoint = () => {
      setIsMedium(
        window.innerWidth >= BREAKPOINTS.md &&
          window.innerWidth < BREAKPOINTS.lg,
      );
    };
    updateBreakpoint();
    window.addEventListener('resize', updateBreakpoint);
    return () => window.removeEventListener('resize', updateBreakpoint);
  }, []);

  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {isPending, data: processInstanceXmlData} = useProcessInstanceXml({
    processDefinitionKey,
  });

  const incidentsCount = useProcessInstanceIncidentsCount(processInstanceKey, {
    enabled: hasIncident,
  });

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
  const hasEndDate = state === 'COMPLETED' || state === 'TERMINATED';

  const processName = getProcessDefinitionName(processInstance);

  return (
    <InstanceHeader
      state={processInstanceState}
      backButton={
        isBreadcrumbVisible ? undefined : (
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            iconDescription="Back"
            tooltipPosition="bottom"
            aria-label="Back to processes"
            onClick={() => navigate(-1)}
          >
            <ArrowLeft />
          </Button>
        )
      }
      headerColumns={headerColumns.filter((name) => {
        if (name === 'Tenant') {
          return isMultiTenancyEnabled;
        }
        if (name === 'Version Tag') {
          return hasVersionTag;
        }
        if (name === 'End Date') {
          return hasEndDate;
        }
        if (name === 'Start Date' || name === 'Called Instances') {
          return !isMedium;
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
                version: processDefinitionVersion?.toString(),
                process: processDefinitionId,
                active: true,
                incidents: true,
                ...(isMultiTenancyEnabled
                  ? {
                      tenant: tenantId,
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
        ...(hasVersionTag
          ? [
              {
                title: 'User-defined label identifying a definition.',
                content: (
                  <VersionTag size="sm" type="outline">
                    {processDefinitionVersionTag}
                  </VersionTag>
                ),
              },
            ]
          : []),
        ...(isMultiTenancyEnabled
          ? [
              {
                title: tenantName,
                content: tenantName,
              },
            ]
          : []),
        ...(!isMedium
          ? [
              {
                title: formatDate(startDate) ?? '--',
                content: formatDate(startDate),
                dataTestId: 'start-date',
              },
            ]
          : []),
        ...(hasEndDate
          ? [
              {
                title: formatDate(endDate ?? null) ?? '--',
                content: formatDate(endDate ?? null),
                dataTestId: 'end-date',
              },
            ]
          : []),
        ...(!isMedium
          ? [
              {
                hideOverflowingContent: false,
                content: (
                  <>
                    {hasCalledProcessInstances(
                      Object.values(
                        processInstanceXmlData?.businessObjects ?? {},
                      ),
                    ) ? (
                      <Link
                        to={Locations.processes({
                          parentInstanceId: processInstanceKey,
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
            ]
          : []),
      ]}
      additionalContent={
        <ProcessInstanceOperations processInstance={processInstance} />
      }
      customContent={
        <HeaderContent>
          <ProcessNameContainer $hasIncident={hasIncident}>
            {hasIncident ? (
              <>
                <ProcessNameLabel>{processName}</ProcessNameLabel>
                <IncidentCount>
                  {incidentsCount === 1
                    ? '1 incident'
                    : `${incidentsCount} incidents`}
                </IncidentCount>
              </>
            ) : (
              processName
            )}
          </ProcessNameContainer>
        </HeaderContent>
      }
    />
  );
};

export {ProcessInstanceHeader};
