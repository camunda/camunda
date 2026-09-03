/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {createPortal} from 'react-dom';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {DangerButton} from 'modules/components/OperationItem/DangerButton';
import {DeleteConfirmationModal} from './DeleteConfirmationModal';

type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
  onExecute: () => void;
  disabled?: boolean;
  title?: string;
};

const Delete: React.FC<Props> = ({
  processInstanceKey,
  onExecute,
  disabled = false,
  title = 'Delete Instance',
}) => {
  const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);
  return (
    <>
      <DangerButton
        type="DELETE"
        onClick={() => setIsDeleteModalVisible(true)}
        title={title}
        disabled={disabled}
        size="sm"
      />
      {isDeleteModalVisible &&
        createPortal(
          <DeleteConfirmationModal
            processInstanceKey={processInstanceKey}
            open={isDeleteModalVisible}
            onConfirm={() => {
              setIsDeleteModalVisible(false);
              onExecute();
            }}
            onCancel={() => setIsDeleteModalVisible(false)}
          />,
          document.body,
        )}
    </>
  );
};

export {Delete};
