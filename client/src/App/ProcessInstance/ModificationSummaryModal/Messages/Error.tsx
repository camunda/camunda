/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {Text} from './styled';
import {Anchor} from 'modules/components/Anchor/styled';
import {ErrorMessage} from 'modules/components/Messages/ErrorMessage';

function getParentAndRootProcessInformation(
  processInstance: null | ProcessInstanceEntity
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
      processInstanceDetailsStore.state.processInstance
    );

  if (parentProcessId === undefined || rootProcessId === undefined) {
    return null;
  }

  return (
    <ErrorMessage
      content={
        <Text>
          {`This set of planned modifications cannot be applied. This instance is a child instance of `}
          <Anchor href={`/processes/${parentProcessId}`} target="_blank">
            {parentProcessName === undefined
              ? parentProcessId
              : `${parentProcessName} - ${parentProcessId}`}
          </Anchor>
          {`, and cannot be canceled entirely. To cancel this instance, the root instance `}
          <Anchor href={`/processes/${rootProcessId}`} target="_blank">
            {rootProcessName === undefined
              ? rootProcessId
              : `${rootProcessName} - ${rootProcessId}`}
          </Anchor>
          {` needs to be canceled.`}
        </Text>
      }
    />
  );
};

export {Error};
