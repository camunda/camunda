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
import {ModificationIcons} from './ModificationIcons';

type Props = {
  elementInstanceKey: string;
  elementName: string;
  elementId: string;
  scopeKeyHierarchy: string[];
};

const VirtualBar = forwardRef<HTMLDivElement, Props>(
  ({elementInstanceKey, elementId, elementName, scopeKeyHierarchy}, ref) => {
    return (
      <Container ref={ref} data-testid={`node-details-${elementInstanceKey}`}>
        <Stack orientation="horizontal" gap={5}>
          <NodeName>{elementName}</NodeName>
        </Stack>
        <ModificationIcons
          elementId={elementId}
          isPlaceholder
          scopeKeyHierarchy={scopeKeyHierarchy}
        />
      </Container>
    );
  },
);

export {VirtualBar};
