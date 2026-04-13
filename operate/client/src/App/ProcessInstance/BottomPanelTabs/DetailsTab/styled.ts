/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Callout as BaseCallout} from '@carbon/react';
import styled from 'styled-components';

const EmptyMessageContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
`;

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-05);
  padding-inline: var(--cds-spacing-05);
`;

const Callout = styled(BaseCallout)`
  min-width: 100%;
`;

export {
  EmptyMessageContainer,
  Container,
  Callout,
};
