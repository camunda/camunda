/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
<<<<<<< HEAD
import {CodeSnippet} from '@carbon/react';
=======
import { black } from '@carbon/colors';
>>>>>>> 2e5c1575 (feat: add tooltip component to actor cell)

const Container = styled.section`
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const OperationLogName = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
`;

const PropertyText = styled.div`
  ${styles.caption01}
`;

<<<<<<< HEAD
const AuthorTooltip = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-02);
`;

const TooltipCodeSnippet = styled(CodeSnippet)`
  color: var(--cds-text-secondary);
`;

export {
  Container,
  OperationLogName,
  PropertyText,
  AuthorTooltip,
  TooltipCodeSnippet,
};
=======
const ActorTooltip = styled.div`
  --cds-layer: ${black};

  .cds--snippet--single {
    block-size: 2rem;
  }
`;

export {Container, OperationLogName, PropertyText, ActorTooltip};
>>>>>>> 2e5c1575 (feat: add tooltip component to actor cell)
