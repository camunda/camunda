/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {
  type BatchOperationType,
  type ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {Button, MenuButton, MenuItem} from '@carbon/react';
import {
  Error,
  Pause,
  Play,
  Tools,
  RetryFailed,
  MigrateAlt,
  type CarbonIconType,
} from '@carbon/react/icons';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {notificationsStore} from 'modules/stores/notifications';
import {
  handleOperationError as handleOperationErrorUtil,
  handleProcessInstanceStateChangeError,
} from 'modules/utils/notifications';
import {useHandleOperationSuccess} from 'modules/utils/processInstance/handleOperationSuccess';
import {tracking} from 'modules/tracking';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import {useDeleteProcessInstance} from 'modules/mutations/processInstance/useDeleteProcessInstance';
import {useResumeProcessInstance} from 'modules/mutations/processInstance/useResumeProcessInstance';
import {useResolveProcessInstanceIncidents} from 'modules/mutations/processInstance/useResolveProcessInstanceIncidents';
import {useSuspendProcessInstance} from 'modules/mutations/processInstance/useSuspendProcessInstance';
import {MigrationHelperModal} from 'modules/components/HelperModal/MigrationHelperModal';
import {CancelConfirmationModal} from 'modules/components/Operations/CancelConfirmationModal';
import {DeleteConfirmationModal} from 'modules/components/Operations/DeleteConfirmationModal';
import {
  AsyncActionTrigger,
  type AsyncActionTriggerProps,
} from 'modules/components/AsyncActionTrigger';
import {ModificationHelperModal} from './ModificationHelperModal';
import {getStateLocally} from 'modules/utils/localStorage';
import {Locations} from 'modules/Routes';

type Props = {
  isCollapsed?: boolean;
  processInstance: ProcessInstance;
};

const ProcessInstanceOperations: React.FC<Props> = ({
  isCollapsed,
  processInstance,
}) => {
  const processInstanceKey = processInstance.processInstanceKey;
  const navigate = useNavigate();
  const handleOperationSuccessUtil = useHandleOperationSuccess();
  const handleOperationSuccess = (operationType: BatchOperationType) => {
    handleOperationSuccessUtil({
      operationType,
      source: 'instance-header',
    });
  };

  const [isDeleteConfirmationOpen, setDeleteConfirmationOpen] = useState(false);
  const [isCancelConfirmationOpen, setCancelConfirmationOpen] = useState(false);
  const [isModificationHelperOpen, setModificationHelperOpen] = useState(false);
  const [isMigrationHelperOpen, setMigrationHelperOpen] = useState(false);

  const {
    mutate: deleteProcessInstance,
    reset: resetDelete,
    status: deleteStatus,
  } = useDeleteProcessInstance(processInstanceKey, {
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
  });
  const {
    mutate: resolveProcessInstanceIncidents,
    reset: resetResolve,
    status: resolveStatus,
  } = useResolveProcessInstanceIncidents(processInstanceKey, {
    shouldSkipResultCheck: true,
    onSuccess: () => handleOperationSuccess('RESOLVE_INCIDENT'),
    onError: ({status}) => handleOperationErrorUtil(status),
  });
  const {
    mutate: suspendProcessInstance,
    reset: resetSuspend,
    status: suspendStatus,
  } = useSuspendProcessInstance(processInstanceKey, {
    onSuccess: () => handleOperationSuccess('SUSPEND_PROCESS_INSTANCE'),
    onError: (error) => handleProcessInstanceStateChangeError(error, 'suspend'),
  });
  const {
    mutate: resumeProcessInstance,
    reset: resetResume,
    status: resumeStatus,
  } = useResumeProcessInstance(processInstanceKey, {
    onSuccess: () => handleOperationSuccess('RESUME_PROCESS_INSTANCE'),
    onError: (error) => handleProcessInstanceStateChangeError(error, 'resume'),
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
      state: 'ACTIVE',
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
        suspended: true,
        incidents: true,
        processDefinitionId: processInstance.processDefinitionId,
        processDefinitionVersion:
          processInstance.processDefinitionVersion.toString(),
        tenantId: processInstance.tenantId,
      }),
    );
  };

  if (modificationsStore.isModificationModeEnabled) {
    return null;
  }

  if (processInstance.state === 'SUSPENDED') {
    return (
      <>
        <CollapsibleOperationsToolbar isCollapsed={isCollapsed}>
          <CollapsibleOperationTrigger
            isCollapsed={isCollapsed}
            status={resumeStatus}
            label="Resume"
            pendingLabel="Resuming..."
            icon={Play}
            title={`Resume Instance ${processInstanceKey}`}
            onClick={() => resumeProcessInstance()}
            onReset={resetResume}
          />
          <CollapsibleOperationTrigger
            isCollapsed={isCollapsed}
            status={cancelStatus}
            label="Cancel"
            pendingLabel="Canceling..."
            icon={Error}
            title={`Cancel Instance ${processInstanceKey}`}
            onClick={() => setCancelConfirmationOpen(true)}
            onReset={resetCancel}
          />
        </CollapsibleOperationsToolbar>
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
          onReset={resetDelete}
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
      <CollapsibleOperationsToolbar isCollapsed={isCollapsed}>
        {processInstance.hasIncident && (
          <CollapsibleOperationTrigger
            isCollapsed={isCollapsed}
            status={resolveStatus}
            label="Retry"
            pendingLabel="Retrying..."
            icon={RetryFailed}
            title={`Retry Instance ${processInstanceKey}`}
            onClick={() => resolveProcessInstanceIncidents()}
            onReset={resetResolve}
          />
        )}
        <CollapsibleOperationTrigger
          isCollapsed={isCollapsed}
          status={suspendStatus}
          label="Suspend"
          pendingLabel="Suspending..."
          icon={Pause}
          title={`Suspend Instance ${processInstanceKey}`}
          onClick={() => suspendProcessInstance()}
          onReset={resetSuspend}
        />
        <CollapsibleOperationTrigger
          isCollapsed={isCollapsed}
          status={cancelStatus}
          label="Cancel"
          pendingLabel="Canceling..."
          icon={Error}
          title={`Cancel Instance ${processInstanceKey}`}
          onClick={() => setCancelConfirmationOpen(true)}
          onReset={resetCancel}
        />
        <CollapsibleOperationTrigger
          isCollapsed={isCollapsed}
          status="idle"
          label="Modify"
          icon={Tools}
          title={`Modify Instance ${processInstanceKey}`}
          onClick={handleOpenModificationHelper}
        />
        <CollapsibleOperationTrigger
          isCollapsed={isCollapsed}
          status="idle"
          label="Migrate"
          icon={MigrateAlt}
          title={`Migrate Instance ${processInstanceKey}`}
          onClick={handleOpenMigrationHelper}
        />
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
  children: React.ReactNode;
  isCollapsed?: boolean;
}> = ({children, isCollapsed}) => {
  return !isCollapsed ? (
    children
  ) : (
    <MenuButton
      size="sm"
      kind="ghost"
      label="Actions"
      menuAlignment="bottom-end"
    >
      {children}
    </MenuButton>
  );
};

interface CollapsibleOperationTriggerProps extends Omit<
  AsyncActionTriggerProps,
  'children'
> {
  isCollapsed?: boolean;
  isDanger?: boolean;
  label: string;
  title?: string;
  icon?: CarbonIconType;
  onClick: () => void;
}

const CollapsibleOperationTrigger: React.FC<
  CollapsibleOperationTriggerProps
> = ({isCollapsed, isDanger, label, title, icon, onClick, ...rest}) => {
  if (isCollapsed) {
    return (
      <MenuItem
        label={label}
        renderIcon={icon}
        onClick={onClick}
        disabled={rest.status === 'pending'}
      />
    );
  }

  return (
    <AsyncActionTrigger {...rest}>
      <Button
        kind={isDanger ? 'danger--ghost' : 'ghost'}
        renderIcon={icon}
        title={title}
        aria-label={title}
        size="sm"
        onClick={onClick}
        disabled={rest.status === 'pending'}
      >
        {label}
      </Button>
    </AsyncActionTrigger>
  );
};

export {ProcessInstanceOperations};
