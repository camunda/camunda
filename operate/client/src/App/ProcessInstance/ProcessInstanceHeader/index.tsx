/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {formatDate} from 'modules/utils/date';
import {getProcessName} from 'modules/utils/instance';
import {Operations} from 'modules/components/Operations';
import {observer} from 'mobx-react';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
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

const getHeaderColumns = (isMultiTenancyEnabled: boolean = false) => {
  return [
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
    ...(isMultiTenancyEnabled
      ? [
          {
            name: 'Tenant',
            skeletonWidth: '34px',
          },
        ]
      : []),

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
  ];
};

const ProcessInstanceHeader: React.FC = observer(() => {
  const {processInstance} = processInstanceDetailsStore.state;
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;
  const headerColumns = getHeaderColumns(isMultiTenancyEnabled);

  if (
    processInstance === null ||
    !processInstanceDetailsDiagramStore.areDiagramDefinitionsAvailable
  ) {
    return <Skeleton headerColumns={headerColumns} />;
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

  const tenantName = authenticationStore.tenantsById?.[tenantId] ?? tenantId;
  const versionColumnTitle = `View process "${getProcessName(
    processInstance,
  )} version ${processVersion}" instances${
    isMultiTenancyEnabled ? ` - ${tenantName}` : ''
  }`;

  return (
    <InstanceHeader
      state={state}
      hideBottomBorder={state === 'INCIDENT'}
      headerColumns={headerColumns.map(({name}) => name)}
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
              {processInstanceDetailsDiagramStore.hasCalledProcessInstances ? (
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
          scopes={['write']}
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
