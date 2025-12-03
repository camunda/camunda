/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {forwardRef} from 'react';
import {NodeName, Container} from '../Bar/styled';
import {Stack} from '@carbon/react';

type Props = {
  elementInstanceKey: string;
  elementName: string;
};

const VirtualBar = forwardRef<HTMLDivElement, Props>(
  ({elementInstanceKey, elementName}, ref) => {
    return (
      <Container ref={ref} data-testid={`node-details-${elementInstanceKey}`}>
        <Stack orientation="horizontal" gap={5}>
          <NodeName>{elementName}</NodeName>
        </Stack>
      </Container>
    );
  },
);

export {VirtualBar};
