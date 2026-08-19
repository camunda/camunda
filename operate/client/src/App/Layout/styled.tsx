/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const PageContent = styled.main<{$hasStickyHeader?: boolean}>`
  padding-top: ${({$hasStickyHeader}) =>
    $hasStickyHeader ? '0' : 'var(--cds-spacing-09)'};
  padding-left: var(--app-sidebar-width, 0);
  transition: padding-left 0.15s ease-out;
  height: ${({$hasStickyHeader}) =>
    $hasStickyHeader ? 'calc(100% - var(--cds-spacing-09))' : '100%'};
`;

export {PageContent};
