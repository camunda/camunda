/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import BasicExpandButton from 'modules/components/ExpandButton';

const ExpandButton = styled(BasicExpandButton)`
  position: absolute;
  left: -24px;
  top: 6px;
  z-index: 2;
`;

const SummaryContainer = styled.div`
  position: relative;
  height: 27px;
`;

const SummaryLabel = styled.div`
  ${({theme, showFullBorder, isSelected, showPartialBorder}) => {
    const colors = theme.colors.flowNodeInstancesTree.foldable.summaryLabel;

    return css`
      position: absolute;
      left: 0;
      top: 0;
      width: 100%;
      margin: 0;
      padding: 0;
      border: none;
      font-size: 14px;
      text-align: left;
      ${showFullBorder && !isSelected
        ? css`
            border-bottom: 1px solid ${colors.borderColor};
          `
        : ''};
      ${showPartialBorder
        ? css`
            &:before {
              content: '';
              position: absolute;
              height: 1px;
              width: 32px;
              bottom: -1px;
              z-index: 1;
              background: ${isSelected ? 'none' : colors.backgroundColor};
            }
          `
        : ''};
    `;
  }}
`;

const FocusButton = styled.button`
  ${({theme, showHoverState}) => {
    return css`
      background: transparent;
      ${showHoverState
        ? css`
            /* Apply hover style to <Bar/>*/
            &:hover + div > div {
              background: ${theme.colors.treeHover};
            }
          `
        : ''};
    `;
  }}
`;

export {ExpandButton, SummaryContainer, SummaryLabel, FocusButton};
