/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';

import {Content, EmptyMessageContainer} from './styled';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {Loading} from '@carbon/react';
import {useVariables} from 'modules/queries/variables/useVariables';
import {VariablesFinalForm} from './VariablesFinalForm';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {isRequestError} from 'modules/request';
import {getForbiddenPermissionsError} from 'modules/constants/permissions';
import {useVariableScopeKey} from 'modules/hooks/variables';
import {EditorProvider} from './Variables/EditorContext/EditorProvider';

const VariablesTab: React.FC = observer(() => {
  const {displayStatus, error} = useVariables();
  const scopeKey = useVariableScopeKey();

  if (displayStatus === 'error') {
    const isForbidden =
      isRequestError(error) &&
      error?.response?.status === HTTP_STATUS_FORBIDDEN;
    const forbidden = getForbiddenPermissionsError('Variables', 'variables');
    return (
      <EmptyMessageContainer>
        <ErrorMessage
          message={
            isForbidden ? forbidden.message : 'Variables could not be fetched'
          }
          additionalInfo={
            isForbidden
              ? forbidden.additionalInfo
              : 'Refresh the page to try again'
          }
        />
      </EmptyMessageContainer>
    );
  }

  if (displayStatus === 'multi-instances') {
    return (
      <EmptyMessageContainer>
        <EmptyMessage
          message="To view the variables, select a single element instance in the
          instance history."
        />
      </EmptyMessageContainer>
    );
  }

  if (displayStatus === 'no-variables') {
    return (
      <Content>
        {scopeKey !== null ? (
          <VariablesFinalForm scopeKey={scopeKey} />
        ) : (
          <EmptyMessageContainer>
            <EmptyMessage message="The element has no variables" />
          </EmptyMessageContainer>
        )}
      </Content>
    );
  }

  return (
    <Content>
      {displayStatus === 'spinner' && (
        <Loading data-testid="variables-spinner" />
      )}
      <EditorProvider>
        {scopeKey !== null && <VariablesFinalForm scopeKey={scopeKey} />}
      </EditorProvider>
    </Content>
  );
});

export {VariablesTab};
