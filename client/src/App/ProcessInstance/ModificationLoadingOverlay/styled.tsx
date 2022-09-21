/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {Spinner as BaseSpinner} from 'modules/components/Spinner';
import {zModificationLoadingOverlay} from 'modules/constants/componentHierarchy';

type OverlayProps = {
  $modificationHeaderHeight: number;
  $footerHeight: number;
};

const Overlay = styled.div<OverlayProps>`
  ${({theme, $modificationHeaderHeight, $footerHeight}) => {
    const colors = theme.colors.processInstance.modifications.loadingOverlay;

    return css`
      background-color: ${colors.backgroundColor};
      color: ${theme.colors.text01};
      z-index: ${zModificationLoadingOverlay};
      display: flex;
      justify-content: center;
      width: 100%;
      position: absolute;
      height: calc(100% - ${$modificationHeaderHeight}px + ${$footerHeight}px);
      margin-top: ${$modificationHeaderHeight}px;
    `;
  }}
`;

const Container = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 35vh;
`;

const Label = styled.strong`
  margin-bottom: 20px;
`;

const Spinner = styled(BaseSpinner)`
  ${({theme}) => {
    const colors =
      theme.colors.processInstance.modifications.loadingOverlay.spinner;

    return css`
      height: 30px;
      width: 30px;
      border: 4px solid ${colors.borderColor};
      border-right-color: transparent;
    `;
  }}
`;

export {Overlay, Container, Label, Spinner};
