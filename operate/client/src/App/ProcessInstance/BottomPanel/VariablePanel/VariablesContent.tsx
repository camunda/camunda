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
import {getScopeId} from 'modules/utils/variables';
import {useVariables} from 'modules/queries/variables/useVariables';
import {VariablesFinalForm} from './VariablesFinalForm';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {isRequestError} from 'modules/request';

const VariablesContent: React.FC = observer(() => {
  const {displayStatus, error} = useVariables();
  const scopeId = getScopeId();

  if (displayStatus === 'error') {
    return (
      <EmptyMessageContainer>
        <ErrorMessage
          message={
            isRequestError(error) &&
            error?.response?.status === HTTP_STATUS_FORBIDDEN
              ? 'Missing permissions to access Variables'
              : 'Variables could not be fetched'
          }
          additionalInfo={
            isRequestError(error) &&
            error?.response?.status === HTTP_STATUS_FORBIDDEN
              ? 'Please contact your organization owner or admin to give you the necessary permissions to access variables'
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
          message="To view the Variables, select a single Flow Node Instance in the
          Instance History."
        />
      </EmptyMessageContainer>
    );
  }

  return (
    <Content>
      {displayStatus === 'spinner' && (
        <Loading data-testid="variables-spinner" />
      )}
      {scopeId !== null && <VariablesFinalForm scopeId={scopeId} />}
    </Content>
  );
});

export {VariablesContent};
