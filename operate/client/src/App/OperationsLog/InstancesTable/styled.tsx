/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
import {CodeSnippet} from '@carbon/react';

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
