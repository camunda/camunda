/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {OperationItem} from 'modules/components/OperationItem';
import {CancelConfirmationModal} from './CancelConfirmationModal';

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
        <CancelConfirmationModal
          processInstanceKey={processInstanceKey}
          open={isCancellationModalVisible}
          onConfirm={() => {
            setIsCancellationModalVisible(false);
            onExecute();
          }}
          onCancel={() => setIsCancellationModalVisible(false)}
        />
      )}
    </>
  );
};

export {Cancel};
