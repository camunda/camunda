/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Menu = styled.ul`
  ${({theme}) => {
    return css`
      display: flex;
      flex-wrap: wrap;
      font-size: 15px;
      font-weight: 500;
      color: ${theme.colors.text02};
    `;
  }}
`;

const Separator = styled.div`
  ${({theme}) => {
    const colors = theme.colors.header;

    return css`
      width: 1px;
      height: 24px;
      background-color: ${colors.separator};
      margin: 0 20px;
    `;
  }}
`;

const LeftSeparator = styled(Separator)`
  margin: 0 20px;
`;

const RightSeparator = styled(Separator)`
  margin: 0 11px 0 15px;
`;

export {Menu, LeftSeparator, RightSeparator};
