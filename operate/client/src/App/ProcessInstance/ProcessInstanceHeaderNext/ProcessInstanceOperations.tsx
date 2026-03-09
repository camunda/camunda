/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import {useNavigate} from 'react-router-dom';
import type {MutationStatus} from '@tanstack/react-query';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.9';
import {MenuButton, MenuItem, type InlineLoadingProps} from '@carbon/react';
import {
  Error,
  Tools,
  RetryFailed,
  MigrateAlt,
  type CarbonIconType,
} from '@carbon/react/icons';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {notificationsStore} from 'modules/stores/notifications';
import {handleOperationError as handleOperationErrorUtil} from 'modules/utils/notifications';
import {useHandleOperationSuccess} from 'modules/utils/processInstance/handleOperationSuccess';
import {tracking} from 'modules/tracking';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import {useDeleteProcessInstance} from 'modules/mutations/processInstance/useDeleteProcessInstance';
import {useResolveProcessInstanceIncidents} from 'modules/mutations/processInstance/useResolveProcessInstanceIncidents';
import {type OperationEntityType} from 'modules/types/operate';
import {MigrationHelperModal} from 'modules/components/HelperModal/MigrationHelperModal';
import {CancelConfirmationModal} from 'modules/components/Operations/CancelConfirmationModal';
import {DeleteConfirmationModal} from 'modules/components/Operations/DeleteConfirmationModal';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {ModificationHelperModal} from './ModificationHelperModal';
import {getStateLocally} from 'modules/utils/localStorage';
import {Locations} from 'modules/Routes';

const COLLAPSE_BREAKPOINT = 1024;
const LOADING_STATUS: Record<
  MutationStatus,
  NonNullable<InlineLoadingProps['status']>
> = {
  idle: 'inactive',
  pending: 'active',
  success: 'finished',
  error: 'error',
};

type Props = {
  processInstance: ProcessInstance;
};

const ProcessInstanceOperations: React.FC<Props> = ({processInstance}) => {
  const processInstanceKey = processInstance.processInstanceKey;
  const navigate = useNavigate();
  const handleOperationSuccessUtil = useHandleOperationSuccess();
  const handleOperationSuccess = (operationType: OperationEntityType) => {
    handleOperationSuccessUtil({
      operationType,
      source: 'instance-header',
    });
  };

  const [isDeleteConfirmationOpen, setDeleteConfirmationOpen] = useState(false);
  const [isCancelConfirmationOpen, setCancelConfirmationOpen] = useState(false);
  const [isModificationHelperOpen, setModificationHelperOpen] = useState(false);
  const [isMigrationHelperOpen, setMigrationHelperOpen] = useState(false);

  const {mutate: deleteProcessInstance, status: deleteStatus} =
    useDeleteProcessInstance(processInstanceKey, {
      shouldSkipResultCheck: true,
      onSuccess: () => handleOperationSuccess('DELETE_PROCESS_INSTANCE'),
      onError: (error) => {
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Failed to delete process instance',
          subtitle: error.message,
          isDismissable: true,
        });
      },
    });
  const {
    mutate: cancelProcessInstance,
    reset: resetCancel,
    status: cancelStatus,
  } = useCancelProcessInstance(processInstanceKey, {
    shouldSkipResultCheck: true,
    onSuccess: () => handleOperationSuccess('CANCEL_PROCESS_INSTANCE'),
    onError: (error) => {
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Failed to cancel process instance',
        subtitle: error.message,
        isDismissable: true,
      });
    },
    onSettled: () => setTimeout(resetCancel, 2000),
  });
  const {
    mutate: resolveProcessInstanceIncidents,
    reset: resetResolve,
    status: resolveStatus,
  } = useResolveProcessInstanceIncidents(processInstanceKey, {
    shouldSkipResultCheck: true,
    onSuccess: () => handleOperationSuccess('RESOLVE_INCIDENT'),
    onError: ({status}) => handleOperationErrorUtil(status),
    onSettled: () => setTimeout(resetResolve, 2000),
  });

  const handleOpenModificationHelper = () => {
    if (getStateLocally()?.hideModificationHelperModal) {
      enterModificationMode();
    } else {
      setModificationHelperOpen(true);
    }
  };

  const enterModificationMode = () => {
    modificationsStore.enableModificationMode();
    tracking.track({
      eventName: 'single-operation',
      operationType: 'MODIFY_PROCESS_INSTANCE',
      source: 'instance-header',
    });
  };

  const handleOpenMigrationHelper = () => {
    if (getStateLocally()?.hideMigrationHelperModal) {
      enterMigrationMode();
    } else {
      setMigrationHelperOpen(true);
    }
  };

  const enterMigrationMode = () => {
    processInstanceMigrationStore.setSourceProcessDefinition({
      processDefinitionKey: processInstance.processDefinitionKey,
      processDefinitionId: processInstance.processDefinitionId,
      version: processInstance.processDefinitionVersion,
      versionTag: processInstance.processDefinitionVersionTag,
      name: processInstance.processDefinitionName,
      tenantId: processInstance.tenantId,
      resourceName: null,
      hasStartForm: false,
    });
    processInstanceMigrationStore.setSelectedInstancesCount(1);
    processInstanceMigrationStore.setBatchOperationQuery({
      ids: [processInstance.processInstanceKey],
    });
    processInstanceMigrationStore.enable();
    tracking.track({eventName: 'process-instance-migration-button-clicked'});
    tracking.track({eventName: 'process-instance-migration-mode-entered'});
    navigate(
      Locations.processes({
        active: true,
        incidents: true,
        process: processInstance.processDefinitionId,
        version: processInstance.processDefinitionVersion.toString(),
        tenant: processInstance.tenantId,
      }),
    );
  };

  if (modificationsStore.isModificationModeEnabled) {
    return null;
  }

  if (processInstance.state !== 'ACTIVE') {
    return (
      <>
        <CollapsibleOperationTrigger
          isDanger
          status={deleteStatus}
          label="Delete"
          pendingLabel="Deleting..."
          title={`Delete Instance ${processInstanceKey}`}
          onClick={() => setDeleteConfirmationOpen(true)}
        />
        <DeleteConfirmationModal
          processInstanceKey={processInstance.processInstanceKey}
          open={isDeleteConfirmationOpen}
          onConfirm={() => {
            setDeleteConfirmationOpen(false);
            deleteProcessInstance();
          }}
          onCancel={() => setDeleteConfirmationOpen(false)}
        />
      </>
    );
  }

  return (
    <>
      <CollapsibleOperationsToolbar breakpoint={COLLAPSE_BREAKPOINT}>
        {(isCollapsed) => (
          <>
            {processInstance.hasIncident && (
              <CollapsibleOperationTrigger
                isCollapsed={isCollapsed}
                status={resolveStatus}
                label="Retry"
                pendingLabel="Retrying..."
                icon={RetryFailed}
                title={`Retry Instance ${processInstanceKey}`}
                onClick={() => resolveProcessInstanceIncidents()}
              />
            )}
            <CollapsibleOperationTrigger
              isCollapsed={isCollapsed}
              status={cancelStatus}
              label="Cancel"
              pendingLabel="Canceling..."
              icon={Error}
              title={`Cancel Instance ${processInstanceKey}`}
              onClick={() => setCancelConfirmationOpen(true)}
            />
            <CollapsibleOperationTrigger
              isCollapsed={isCollapsed}
              label="Modify"
              icon={Tools}
              title={`Modify Instance ${processInstanceKey}`}
              onClick={handleOpenModificationHelper}
            />
            <CollapsibleOperationTrigger
              isCollapsed={isCollapsed}
              label="Migrate"
              icon={MigrateAlt}
              title={`Migrate Instance ${processInstanceKey}`}
              onClick={handleOpenMigrationHelper}
            />
          </>
        )}
      </CollapsibleOperationsToolbar>
      {isModificationHelperOpen && (
        <ModificationHelperModal
          open={isModificationHelperOpen}
          onClose={() => setModificationHelperOpen(false)}
          onSubmit={() => {
            setModificationHelperOpen(false);
            enterModificationMode();
          }}
        />
      )}
      {isMigrationHelperOpen && (
        <MigrationHelperModal
          open={isMigrationHelperOpen}
          onClose={() => setMigrationHelperOpen(false)}
          onSubmit={() => {
            setMigrationHelperOpen(false);
            enterMigrationMode();
          }}
        />
      )}
      {isCancelConfirmationOpen && (
        <CancelConfirmationModal
          processInstanceKey={processInstanceKey}
          open={isCancelConfirmationOpen}
          onCancel={() => setCancelConfirmationOpen(false)}
          onConfirm={() => {
            setCancelConfirmationOpen(false);
            cancelProcessInstance();
          }}
        />
      )}
    </>
  );
};

const CollapsibleOperationsToolbar: React.FC<{
  breakpoint: number;
  children: (isCollapsed: boolean) => React.ReactNode;
}> = ({breakpoint, children}) => {
  const [isCollapsed, setIsCollapsed] = useState(
    window.innerWidth < breakpoint,
  );

  useEffect(() => {
    const handleResize = () => {
      setIsCollapsed(window.innerWidth < breakpoint);
    };
    window.addEventListener('resize', handleResize, {passive: true});
    return () => window.removeEventListener('resize', handleResize);
  }, [breakpoint]);

  return !isCollapsed ? (
    children(isCollapsed)
  ) : (
    <MenuButton
      size="sm"
      kind="ghost"
      label="Actions"
      menuAlignment="bottom-end"
    >
      {children(isCollapsed)}
    </MenuButton>
  );
};

type CollapsibleOperationTriggerProps = {
  status?: MutationStatus;
  isCollapsed?: boolean;
  isDanger?: boolean;
  label: string;
  pendingLabel?: string;
  title?: string;
  icon?: CarbonIconType;
  onClick: () => void;
};

const CollapsibleOperationTrigger: React.FC<
  CollapsibleOperationTriggerProps
> = (props) => {
  if (props.isCollapsed) {
    return (
      <MenuItem
        label={props.label}
        renderIcon={props.icon}
        onClick={props.onClick}
        disabled={props.status === 'pending'}
      />
    );
  }

  return (
    <AsyncActionButton
      status={LOADING_STATUS[props.status ?? 'idle']}
      inlineLoadingProps={{description: props.pendingLabel}}
      buttonProps={{
        kind: props.isDanger ? 'danger--ghost' : 'ghost',
        renderIcon: props.icon,
        title: props.title,
        size: 'sm',
        onClick: props.onClick,
        disabled: props.status === 'pending',
      }}
    >
      {props.label}
    </AsyncActionButton>
  );
};

export {ProcessInstanceOperations};
