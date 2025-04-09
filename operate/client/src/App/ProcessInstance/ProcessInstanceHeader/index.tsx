/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import isNil from 'lodash/isNil';
import {formatDate} from 'modules/utils/date';
import {getProcessName} from 'modules/utils/instance';
import {Operations} from 'modules/components/Operations';
import {observer} from 'mobx-react';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {variablesStore} from 'modules/stores/variables';
import {Link} from 'modules/components/Link';
import {Locations, Paths} from 'modules/Routes';
import {Restricted} from 'modules/components/Restricted';
import {panelStatesStore} from 'modules/stores/panelStates';
import {modificationsStore} from 'modules/stores/modifications';
import {tracking} from 'modules/tracking';
import {InstanceHeader} from 'modules/components/InstanceHeader';
import {Skeleton} from 'modules/components/InstanceHeader/Skeleton';
import {notificationsStore} from 'modules/stores/notifications';
import {authenticationStore} from 'modules/stores/authentication';
import {processStore} from 'modules/stores/process';
import {VersionTag} from './styled';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {hasCalledProcessInstances} from 'modules/bpmn-js/utils/hasCalledProcessInstances';

const headerColumns = [
  'Process Name',
  'Process Instance Key',
  'Version',
  'Version Tag',
  'Tenant',
  'Start Date',
  'End Date',
  'Parent Process Instance Key',
  'Called Process Instances',
] as const;

const skeletonColumns: {
  name: (typeof headerColumns)[number];
  skeletonWidth: string;
}[] = [
  {
    name: 'Process Name',
    skeletonWidth: '94px',
  },
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
    name: 'Parent Process Instance Key',
    skeletonWidth: '142px',
  },
  {
    name: 'Called Process Instances',
    skeletonWidth: '142px',
  },
] as const;

const ProcessInstanceHeader: React.FC = observer(() => {
  const {processInstance} = processInstanceDetailsStore.state;
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;
  const processId = processInstance?.processId;
  const {
    state: {process, status},
  } = processStore;

  useEffect(() => {
    if (processId !== undefined) {
      processStore.fetchProcess(processId);
    }
  }, [processId]);

  useEffect(() => {
    return processStore.reset;
  }, []);

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {isPending, data: processInstanceXmlData} = useProcessInstanceXml({
    processDefinitionKey,
  });

  if (
    processInstance === null ||
    ['fetching', 'initial'].includes(status) ||
    isPending
  ) {
    return <Skeleton headerColumns={skeletonColumns} />;
  }

  const {
    id,
    processVersion,
    tenantId,
    startDate,
    endDate,
    parentInstanceId,
    state,
    bpmnProcessId,
  } = processInstance;

  const versionTag = process?.versionTag;
  const tenantName = authenticationStore.tenantsById?.[tenantId] ?? tenantId;
  const versionColumnTitle = `View process "${getProcessName(
    processInstance,
  )} version ${processVersion}" instances${
    isMultiTenancyEnabled ? ` - ${tenantName}` : ''
  }`;
  const hasVersionTag = !isNil(versionTag);

  return (
    <InstanceHeader
      state={state}
      hideBottomBorder={state === 'INCIDENT'}
      headerColumns={headerColumns.filter((name) => {
        if (name === 'Tenant') {
          return isMultiTenancyEnabled;
        }
        if (name === 'Version Tag') {
          return hasVersionTag;
        }
        return true;
      })}
      bodyColumns={[
        {
          title: getProcessName(processInstance),
          content: getProcessName(processInstance),
        },
        {title: id, content: id},
        {
          hideOverflowingContent: false,
          content: (
            <Link
              to={Locations.processes({
                version: processVersion?.toString(),
                process: bpmnProcessId,
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
              {processVersion}
            </Link>
          ),
        },
        ...(hasVersionTag
          ? [
              {
                title: 'User-defined label identifying a definition.',
                content: (
                  <VersionTag size="sm" type="outline">
                    {versionTag}
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
        {
          title: formatDate(startDate) ?? '--',
          content: formatDate(startDate),
          dataTestId: 'start-date',
        },
        {
          title: formatDate(endDate) ?? '--',
          content: formatDate(endDate),
          dataTestId: 'end-date',
        },
        {
          title: parentInstanceId ?? 'None',
          hideOverflowingContent: false,
          content: (
            <>
              {parentInstanceId !== null ? (
                <Link
                  to={Paths.processInstance(parentInstanceId)}
                  title={`View parent instance ${parentInstanceId}`}
                  aria-label={`View parent instance ${parentInstanceId}`}
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'process-details-parent-details',
                    });
                  }}
                >
                  {parentInstanceId}
                </Link>
              ) : (
                'None'
              )}
            </>
          ),
        },
        {
          hideOverflowingContent: false,
          content: (
            <>
              {hasCalledProcessInstances(
                Object.values(processInstanceXmlData?.businessObjects ?? {}),
              ) ? (
                <Link
                  to={Locations.processes({
                    parentInstanceId: id,
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
        <Restricted
          resourceBasedRestrictions={{
            scopes: ['UPDATE_PROCESS_INSTANCE', 'DELETE_PROCESS_INSTANCE'],
            permissions: processInstanceDetailsStore.getPermissions(),
          }}
        >
          <>
            <Operations
              instance={processInstance}
              onOperation={(operationType: OperationEntityType) =>
                processInstanceDetailsStore.activateOperation(operationType)
              }
              onError={({operationType, statusCode}) => {
                processInstanceDetailsStore.deactivateOperation(operationType);

                notificationsStore.displayNotification({
                  kind: 'error',
                  title: 'Operation could not be created',
                  subtitle:
                    statusCode === 403
                      ? 'You do not have permission'
                      : undefined,
                  isDismissable: true,
                });
              }}
              onSuccess={(operationType) => {
                tracking.track({
                  eventName: 'single-operation',
                  operationType,
                  source: 'instance-header',
                });
              }}
              forceSpinner={
                variablesStore.hasActiveOperation ||
                processInstance?.hasActiveOperation
              }
              isInstanceModificationVisible={
                !modificationsStore.isModificationModeEnabled
              }
              permissions={processInstanceDetailsStore.getPermissions()}
            />
          </>
        </Restricted>
      }
    />
  );
});

export {ProcessInstanceHeader};
