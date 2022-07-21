/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {Link as LinkComponent} from 'modules/components/Link';
import {styles} from '@carbon/elements';

const Container = styled.div`
  ${({theme}) => {
    return css`
      color: ${theme.colors.text02};
      padding: 4px 20px;
      display: flex;
      ${styles.label02};
      align-items: center;
      border-bottom: 1px solid ${theme.colors.borderColor};
      min-height: 30px;
    `;
  }}
`;

const ellipsisCss = css`
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
`;

const Link = styled(LinkComponent)`
  ${({theme}) => {
    return css`
      color: ${theme.colors.text02};
      text-decoration: none;
      ${ellipsisCss};
    `;
  }}
`;

const Separator = styled.div`
  margin: 0 10px 2px 11px;
  ${styles.label02};
`;

const CurrentInstance = styled.span`
  ${ellipsisCss};
`;

export {Container, Link, Separator, CurrentInstance};
