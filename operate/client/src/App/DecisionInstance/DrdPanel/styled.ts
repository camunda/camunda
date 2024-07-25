/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {PAGE_TOP_PADDING} from 'modules/constants';
import {zOverlayCollapsable} from 'modules/constants/componentHierarchy';
import styled from 'styled-components';

const Container = styled.div`
  display: flex;
  position: absolute;
  height: calc(100vh - ${PAGE_TOP_PADDING}px);
  right: 0;
  z-index: ${zOverlayCollapsable};
`;

const Handle = styled.div`
  cursor: ew-resize;
  position: absolute;
  left: -5px;
  width: 10px;
  height: 100%;
`;

const Panel = styled.section`
  width: 540px;
  border-left: solid 1px var(--cds-border-subtle-01);
  &.resizing {
    border-left-color: var(--cds-border-interactive);
  }
`;

export {Container, Handle, Panel};
