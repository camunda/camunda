/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const PageWrapper = styled.div`
  display: flex;
  flex-direction: row;
  height: 100%;
  padding-top: var(--cds-spacing-09);
  overflow: hidden;
`;

const PageContent = styled.main`
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: hidden;
`;

const CopilotGlobalContainer = styled.div`
  flex-shrink: 0;
  height: 100%;
  border-left: 1px solid var(--cds-border-subtle-01);
`;

export {PageWrapper, PageContent, CopilotGlobalContainer};
