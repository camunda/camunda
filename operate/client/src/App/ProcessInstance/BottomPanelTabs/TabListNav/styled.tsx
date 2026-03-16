/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {styled} from 'styled-components';

const Button = styled.button`
  &.hidden {
    display: none;
    visibility: hidden;
  }
`;

const Nav = styled.nav`
  border-bottom: 1px solid var(--cds-border-subtle);

  .cds--tabs__nav-item-label:not(:last-child) {
    margin-right: var(--cds-spacing-02);
  }
`;

export {Nav, Button};
