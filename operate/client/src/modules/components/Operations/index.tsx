/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OperationItems} from 'modules/components/OperationItems';
import {InlineLoading} from '@carbon/react';
import {LoadingIndicatorContainer, OperationsContainer} from './styled';
import {OperationRenderer} from './OperationRenderer';
import type {OperationConfig} from './types';

type Props = {
  operations: Array<OperationConfig | null>;
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
      {isLoading ? (
        <LoadingIndicatorContainer>
          <InlineLoading
            data-testid="operation-spinner"
            title={
              loadingMessage ||
              `Instance ${processInstanceKey} has scheduled Operations`
            }
          />
        </LoadingIndicatorContainer>
      ) : null}
      <OperationItems>
        {operations.map((operation, index) => (
          <OperationRenderer
            key={operation?.type ?? `placeholder-${index}`}
            operation={operation}
            processInstanceKey={processInstanceKey}
          />
        ))}
      </OperationItems>
    </OperationsContainer>
  );
};

export {Operations};
