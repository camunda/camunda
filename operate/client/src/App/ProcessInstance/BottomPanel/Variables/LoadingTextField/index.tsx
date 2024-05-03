/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LoadingStateContainer} from './styled';
import {IconTextInputField} from 'modules/components/IconTextInputField';
import {Loading} from '@carbon/react';

type Props = React.ComponentProps<typeof IconTextInputField> & {
  isLoading: boolean;
};

const LoadingTextfield: React.FC<Props> = ({isLoading, ...props}) => {
  if (isLoading) {
    return (
      <LoadingStateContainer>
        <Loading small data-testid="full-variable-loader" />
        <IconTextInputField {...props} />
      </LoadingStateContainer>
    );
  }

  return <IconTextInputField {...props} />;
};

export {LoadingTextfield};
