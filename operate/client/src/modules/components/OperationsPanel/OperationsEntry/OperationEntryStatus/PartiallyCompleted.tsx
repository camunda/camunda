/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {StatusContainer, Text, Tooltip, WarningAltFilled} from './styled';
import {Information} from '@carbon/react/icons';

const PartiallyCompleted: React.FC = () => {
  return (
    <StatusContainer>
      <WarningAltFilled />
      <Text>Partially completed</Text>
      <Tooltip
        align="bottom"
        description="The operation is partially completed due to an error. Please try again."
      >
        <Information />
      </Tooltip>
    </StatusContainer>
  );
};

export {PartiallyCompleted};
