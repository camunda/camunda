/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LoadingStateContainer} from './styled';
import {Loading, TextInput} from '@carbon/react';

type Props = React.ComponentProps<typeof TextInput> & {
  isLoading: boolean;
};

const LoadingTextfield: React.FC<Props> = ({isLoading, ...props}) => {
  if (isLoading) {
    return (
      <LoadingStateContainer>
        <Loading small data-testid="full-variable-loader" />
        <TextInput {...props} />
      </LoadingStateContainer>
    );
  }

  return <TextInput {...props} />;
};

export {LoadingTextfield};
