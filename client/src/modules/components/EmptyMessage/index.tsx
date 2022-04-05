/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import * as Styled from './styled';

type Props = {
  message: string | React.ReactNode;
};

const EmptyMessage: React.FC<Props> = ({message, ...props}) => {
  return (
    <Styled.EmptyMessage {...props}>
      {typeof message === 'string'
        ? message
            .split('\n')
            .map((item, index) => <span key={index}>{item}</span>)
        : message}
    </Styled.EmptyMessage>
  );
};

export {EmptyMessage};
