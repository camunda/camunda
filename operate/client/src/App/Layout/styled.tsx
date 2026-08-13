/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

// The legacy Carbon header is `position: fixed`, so PageContent needs padding-top
// to clear it, and can safely be height: 100% since the fixed header takes no
// space in normal flow. The DS AppHeader is `position: sticky` instead — it
// reserves its own height in normal flow, so on top of it: padding-top would
// double-count the header height (empty gap below the header), and height: 100%
// would overflow #root's fixed 100vh by the header's own height (clipped/
// scrolling content at the bottom).
const PageContent = styled.main<{$hasStickyHeader?: boolean}>`
  padding-top: ${({$hasStickyHeader}) =>
    $hasStickyHeader ? '0' : 'var(--cds-spacing-09)'};
  padding-left: var(--app-sidebar-width, 0);
  transition: padding-left 0.15s ease-out;
  height: ${({$hasStickyHeader}) =>
    $hasStickyHeader ? 'calc(100% - var(--cds-spacing-09))' : '100%'};
`;

export {PageContent};
