/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {Link} from '@carbon/react';
import {ActionableNotification, Text} from './styled';
import type {ProcessInstanceEntity} from 'modules/types/operate';

function getParentAndRootProcessInformation(
  processInstance: null | ProcessInstanceEntity,
): {
  parentProcessName?: string;
  parentProcessId?: string;
  rootProcessName?: string;
  rootProcessId?: string;
} {
  const parentProcess =
    processInstance?.callHierarchy[processInstance.callHierarchy.length - 1];
  const rootProcess = processInstance?.callHierarchy[0];

  return {
    parentProcessName: parentProcess?.processDefinitionName,
    parentProcessId: parentProcess?.instanceId,
    rootProcessName: rootProcess?.processDefinitionName,
    rootProcessId: rootProcess?.instanceId,
  };
}

const Error: React.FC = () => {
  const {parentProcessId, parentProcessName, rootProcessId, rootProcessName} =
    getParentAndRootProcessInformation(
      processInstanceDetailsStore.state.processInstance,
    );

  if (parentProcessId === undefined || rootProcessId === undefined) {
    return null;
  }

  return (
    <ActionableNotification
      kind="error"
      inline
      hideCloseButton
      lowContrast
      title=""
      children={
        <Text>
          {`This set of planned modifications cannot be applied. This instance is a child instance of `}
          <Link href={`/processes/${parentProcessId}`} target="_blank">
            {parentProcessName === undefined
              ? parentProcessId
              : `${parentProcessName} - ${parentProcessId}`}
          </Link>
          {`, and cannot be canceled entirely. To cancel this instance, the root instance `}
          <Link href={`/processes/${rootProcessId}`} target="_blank">
            {rootProcessName === undefined
              ? rootProcessId
              : `${rootProcessName} - ${rootProcessId}`}
          </Link>
          {` needs to be canceled.`}
        </Text>
      }
      actionButtonLabel=""
    />
  );
};

export {Error};
