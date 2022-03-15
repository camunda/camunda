/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const CollapsedPanel = styled.div`
  min-width: 56px;
`;

const ExpandedPanel = styled.div`
  min-width: 328px;
  height: 100%;
`;

const Header = styled.header`
  width: 100%;
  height: 38px;
`;

const Content = styled.div`
  width: 100%;
  height: calc(100% - 38px);
`;

export {CollapsedPanel, ExpandedPanel, Header, Content};
