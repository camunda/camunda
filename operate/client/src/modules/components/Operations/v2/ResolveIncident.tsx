/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstance} from '@vzeta/camunda-api-zod-schemas';
import {Restricted} from 'modules/components/Restricted';
import {useOperations} from 'modules/queries/operations/useOperations';
import {ACTIVE_OPERATION_STATES} from 'modules/constants';
import {OperationItem} from 'modules/components/OperationItem';

type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
  permissions?: ResourceBasedPermissionDto[] | null;
  applyOperation: (operationType: OperationEntityType) => Promise<void>;
};

const ResolveIncident: React.FC<Props> = ({
  processInstanceKey,
  permissions,
  applyOperation,
}) => {
  const {data: operations} = useOperations();

  const isOperationActive = (operationType: OperationEntityType) => {
    return operations?.some(
      (operation) =>
        operation.type === operationType &&
        ACTIVE_OPERATION_STATES.includes(operation.state),
    );
  };

  return (
    <>
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['UPDATE_PROCESS_INSTANCE'],
          permissions,
        }}
      >
        <OperationItem
          type="RESOLVE_INCIDENT"
          onClick={() => applyOperation('RESOLVE_INCIDENT')}
          title={`Retry Instance ${processInstanceKey}`}
          disabled={isOperationActive('RESOLVE_INCIDENT')}
          size="sm"
        />
      </Restricted>
    </>
  );
};

export {ResolveIncident};
