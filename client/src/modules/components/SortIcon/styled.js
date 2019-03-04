/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as DefaultUp} from 'modules/components/Icon/up.svg';
import {ReactComponent as DefaultDown} from 'modules/components/Icon/down.svg';
import {themed} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';

export const SortIcon = styled.span``;

const inactiveStyle = css`
  ${({sortOrder}) => (!sortOrder ? 'opacity: 0.4' : '')};
`;

const sortIconStyle = css`
  height: 16px;
  width: 16px;

  ${inactiveStyle};
`;

export const Up = themed(styled(withStrippedProps(['sortOrder'])(DefaultUp))`
  ${sortIconStyle};
`);

export const Down = themed(styled(
  withStrippedProps(['sortOrder'])(DefaultDown)
)`
  ${sortIconStyle};
`);
