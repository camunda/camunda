/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OperationItems} from 'modules/components/OperationItems';
import {InlineLoading} from '@carbon/react';
import {LoadingSlot, OperationsContainer} from './styled';
import {OperationRenderer} from './OperationRenderer';
import type {OperationSlot} from './types';

type Props = {
  operations: OperationSlot[];
  processInstanceKey: string;
  isLoading?: boolean;
  loadingMessage?: string;
};

const Operations: React.FC<Props> = ({
  operations,
  processInstanceKey,
  isLoading = false,
  loadingMessage,
}) => {
  return (
    <OperationsContainer orientation="horizontal">
      <OperationItems>
        <LoadingSlot aria-hidden={isLoading ? undefined : true}>
          {isLoading ? (
            <InlineLoading
              iconDescription={
                loadingMessage ||
                `Instance ${processInstanceKey} has scheduled Operations`
              }
            />
          ) : null}
        </LoadingSlot>
        {operations.map((operation, index) => (
          <OperationRenderer
            key={index}
            operation={operation}
            processInstanceKey={processInstanceKey}
          />
        ))}
      </OperationItems>
    </OperationsContainer>
  );
};

export {Operations};
