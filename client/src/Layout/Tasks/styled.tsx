/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import styled, {css} from 'styled-components';
import {LoadingOverlay} from 'modules/components/LoadingOverlay';

const EmptyMessage = styled.div`
  border: 1px solid ${({theme}) => theme.colors.ui05};
  border-radius: 3px;

  margin: 16px 20px 0 20px;
  padding: 38px 39px;
  text-align: center;
  font-size: 14px;
  color: ${({theme}) => theme.colors.ui07};
  background-color: ${({theme}) => theme.colors.ui04};
`;

const UL = styled.ul`
  overflow-y: auto;
  width: 100%;
  height: 100%;
`;

type ContainerProps = {
  isLoading: boolean;
};

const Container = styled.div<ContainerProps>`
  ${({isLoading}) => {
    return css`
      width: 100%;
      height: 100%;
      overflow-y: hidden;

      ${isLoading
        ? css`
            position: relative;
          `
        : ''}

      ${LoadingOverlay} {
        position: absolute;
        align-items: flex-start;
        padding-top: 12.5%;
      }
    `;
  }}
`;

export {EmptyMessage, UL, Container};
