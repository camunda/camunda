/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {ValueTextField} from '../styled';
import {LoadingStateContainer, Overlay} from './styled';

type Props = React.ComponentProps<typeof ValueTextField> & {
  isLoading: boolean;
};

const LoadingTextarea: React.FC<Props> = ({isLoading, ...props}) => {
  if (isLoading) {
    return (
      <LoadingStateContainer data-testid="textarea-loading-overlay">
        <Overlay />
        <ValueTextField {...props} />
      </LoadingStateContainer>
    );
  }

  return <ValueTextField {...props} />;
};

export {LoadingTextarea};
