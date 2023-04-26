/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Message, AdditionalInfo} from './styled';
import {Stack} from '@carbon/react';

type Props = {
  message: string;
  additionalInfo?: string;
};

const EmptyMessage: React.FC<Props> = ({message, additionalInfo, ...props}) => {
  return (
    <Stack {...props} gap={3}>
      <Message>{message}</Message>
      {additionalInfo && <AdditionalInfo>{additionalInfo}</AdditionalInfo>}
    </Stack>
  );
};

export {EmptyMessage};
