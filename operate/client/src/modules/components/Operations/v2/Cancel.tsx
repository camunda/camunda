/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Modal} from '@carbon/react';
import {type ProcessInstance} from '@vzeta/camunda-api-zod-schemas/8.8';
import {Paths} from 'modules/Routes';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import {OperationItem} from 'modules/components/OperationItem';
import {Link} from 'modules/components/Link';
import {useRootInstanceId} from 'modules/queries/callHierarchy/useRootInstanceId';
import {notificationsStore} from 'modules/stores/notifications';
import {useProcessInstanceOperationsContext} from 'App/ProcessInstance/ProcessInstanceHeader/processInstanceOperationsContext';

type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
};

const Cancel: React.FC<Props> = ({processInstanceKey}) => {
  const [isCancellationModalVisible, setIsCancellationModalVisible] =
    useState(false);

  const {data: rootInstanceId} = useRootInstanceId({
    enabled: isCancellationModalVisible,
  });

  const processInstanceOperationsContext =
    useProcessInstanceOperationsContext();
  const {isPending, onMutate, onError} =
    processInstanceOperationsContext?.cancellation ?? {};

  const cancelProcessInstanceMutation = useCancelProcessInstance(
    processInstanceKey,
    {
      onError: (error) => {
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Failed to cancel process instance',
          subtitle: error.message,
          isDismissable: true,
        });
        onError?.(error);
      },
      onMutate,
    },
  );

  return (
    <>
      <OperationItem
        type="CANCEL_PROCESS_INSTANCE"
        onClick={() => setIsCancellationModalVisible(true)}
        title={`Cancel Instance ${processInstanceKey}`}
        disabled={isPending}
        size="sm"
      />

      {isCancellationModalVisible && (
        <>
          {!rootInstanceId ? (
            <Modal
              open={isCancellationModalVisible}
              preventCloseOnClickOutside
              modalHeading="Apply Operation"
              primaryButtonText="Apply"
              secondaryButtonText="Cancel"
              onRequestSubmit={() => {
                setIsCancellationModalVisible(false);
                cancelProcessInstanceMutation.mutate();
              }}
              onRequestClose={() => setIsCancellationModalVisible(false)}
              size="md"
              data-testid="confirm-cancellation-modal"
            >
              <p>{`About to cancel Instance ${processInstanceKey}. In case there are called instances, these will be canceled too.`}</p>
              <p>Click "Apply" to proceed.</p>
            </Modal>
          ) : (
            <Modal
              open={isCancellationModalVisible}
              preventCloseOnClickOutside
              modalHeading="Cancel Instance"
              passiveModal
              onRequestClose={() => setIsCancellationModalVisible(false)}
              size="md"
              data-testid="passive-cancellation-modal"
            >
              <p>
                To cancel this instance, the root instance{' '}
                <Link
                  to={Paths.processInstance(rootInstanceId)}
                  title={`View root instance ${rootInstanceId}`}
                >
                  {rootInstanceId}
                </Link>{' '}
                needs to be canceled. When the root instance is canceled all the
                called instances will be canceled automatically.
              </p>
            </Modal>
          )}
        </>
      )}
    </>
  );
};

export {Cancel};
