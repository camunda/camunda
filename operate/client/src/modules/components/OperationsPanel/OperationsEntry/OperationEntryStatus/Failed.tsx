/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip} from '@carbon/react';
import {StatusContainer, Text, ErrorFilled} from './styled';
import {Information} from '@carbon/react/icons';

const Failed: React.FC = () => {
  return (
    <StatusContainer>
      <ErrorFilled />
      <Text>Failed</Text>
      <Tooltip
        align="bottom-left"
        description="An error happened during the operation. Please try again."
      >
        <Information />
      </Tooltip>
    </StatusContainer>
  );
};

export {Failed};
