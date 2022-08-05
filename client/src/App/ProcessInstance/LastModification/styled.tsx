/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {LinkButton} from 'modules/components/LinkButton';

const Container = styled.div`
  ${({theme}) => {
    const colors =
      theme.colors.processInstance.modifications.footer.lastModification;

    return css`
      display: flex;
      align-items: center;
      height: 32px;
      ${styles.label02};
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
      box-shadow: ${theme.shadows.modificationMode.lastModification};
      padding: 0 16px;
    `;
  }}
`;

const Button = styled(LinkButton)`
  text-decoration: none;
  font-weight: 500;
  padding-left: 15px;
`;

const ModificationDetail = styled.div`
  ${({theme}) => {
    const colors =
      theme.colors.processInstance.modifications.footer.lastModification;

    return css`
      display: inline-flex;
      font-weight: 500;
      position: relative;
      padding-right: 18px;
      &:after {
        content: ' ';
        position: absolute;
        right: 0;
        height: 19px;
        width: 1px;
        background-color: ${colors.separatorColor};
      }
    `;
  }}
`;

export {Container, Button, ModificationDetail};
