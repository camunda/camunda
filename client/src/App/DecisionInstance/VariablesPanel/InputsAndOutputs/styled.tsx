/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {StatusMessage} from 'modules/components/StatusMessage';
import styled, {css} from 'styled-components';
import {BaseBlock} from 'modules/components/Skeleton';

const Panel = styled.div`
  ${({theme}) => {
    const colors = theme.colors.decisionInstance;

    return css`
      width: 50%;
      height: 100%;
      display: flex;
      flex-direction: column;
      background-color: ${colors.backgroundColor};

      &:first-of-type {
        border-right: 1px solid ${colors.borderColor};
        width: calc(50% - 1px);
      }

      & ${StatusMessage} {
        width: 100%;
        height: 58%;
      }
    `;
  }}
`;

const Title = styled.h2`
  ${({theme}) => {
    const {colors} = theme;

    return css`
      font-family: IBM Plex Sans;
      font-size: 16px;
      color: ${colors.text01};
      font-weight: 500;
      padding: 28px 0 0 20px;
      margin: 0;
    `;
  }}
`;

type SkeletonBlockProps = {
  $width?: string;
};

const SkeletonBlock = styled(BaseBlock)<SkeletonBlockProps>`
  ${({$width}) => {
    return css`
      width: min(${$width ?? css`90%`}, 90%);
      height: 15px;
    `;
  }}
`;

export {Panel, Title, SkeletonBlock};
