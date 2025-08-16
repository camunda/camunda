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
import {OperationItem} from 'modules/components/OperationItem';
import {Link} from 'modules/components/Link';
import {useCallHierarchy} from 'modules/queries/callHierarchy/useCallHierarchy';
type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
  onExecute: () => void;
  disabled?: boolean;
};

const Cancel: React.FC<Props> = ({
  processInstanceKey,
  onExecute,
  disabled = false,
}) => {
  const [isCancellationModalVisible, setIsCancellationModalVisible] =
    useState(false);

  const {data: callHierarchy} = useCallHierarchy(
    {processInstanceKey},
    {enabled: isCancellationModalVisible},
  );
  const rootInstanceId = callHierarchy?.[0]?.processInstanceKey;

  const confirmation = {
    title: 'Apply Operation',
    message: `About to cancel Instance ${processInstanceKey}. In case there are called instances, these will be canceled too.`,
    primaryButtonText: 'Apply',
    secondaryButtonText: 'Cancel',
  };

  return (
    <>
      <OperationItem
        type="CANCEL_PROCESS_INSTANCE"
        onClick={() => setIsCancellationModalVisible(true)}
        title={`Cancel Instance ${processInstanceKey}`}
        disabled={disabled}
        size="sm"
      />

      {isCancellationModalVisible && (
        <>
          {!rootInstanceId ? (
            <Modal
              open={isCancellationModalVisible}
              preventCloseOnClickOutside
              modalHeading={confirmation.title}
              primaryButtonText={confirmation.primaryButtonText}
              secondaryButtonText={confirmation.secondaryButtonText}
              onRequestSubmit={() => {
                setIsCancellationModalVisible(false);
                onExecute();
              }}
              onRequestClose={() => setIsCancellationModalVisible(false)}
              size="md"
              data-testid="confirm-cancellation-modal"
            >
              <p>{confirmation.message}</p>
              <p>Click "{confirmation.primaryButtonText}" to proceed.</p>
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
