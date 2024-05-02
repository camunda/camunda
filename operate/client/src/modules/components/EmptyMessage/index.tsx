/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Message, AdditionalInfo, Stack} from './styled';

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
