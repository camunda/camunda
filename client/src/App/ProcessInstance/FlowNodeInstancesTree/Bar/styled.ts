/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import BasicFlowNodeIcon from 'modules/components/FlowNodeIcon';

const NodeIcon = styled(BasicFlowNodeIcon)`
  ${({theme, isSelected}) => {
    const opacity = theme.opacity.flowNodeInstancesTree.bar.nodeIcon;

    return css`
      color: ${theme.colors.text02};
      opacity: ${isSelected ? opacity.selected : opacity.default};
    `;
  }}
`;

type ContainerProps = {
  $isSelected?: boolean;
  $hasTopBorder?: boolean;
};

const Container = styled.div<ContainerProps>`
  ${({theme, $isSelected, $hasTopBorder}) => {
    return css`
      display: flex;
      height: 27px;
      font-size: 13px;
      min-width: 200px;
      align-items: center;
      flex-grow: 1;
      background: ${theme.colors.itemEven};
      border-color: ${theme.colors.borderColor};
      border-width: 1px 0px 0px 1px;
      border-style: solid;

      ${$isSelected
        ? css`
            border-color: ${theme.colors.borderColor};
            border-width: 1px 0px 0px 1px;
            background: ${theme.colors.selectedOdd};
            color: ${theme.colors.white};

            /* Bottom Border */
            &:before {
              content: '';
              position: absolute;
              bottom: 0px;
              left: 0px;
              width: 100%;
              height: 1px;
              z-index: 1;
            }
          `
        : ''};

      ${!$hasTopBorder &&
      css`
        border-top: none;
      `}
    `;
  }}
`;

type NodeNameProps = {
  isSelected?: boolean;
  isBold?: boolean;
};

const NodeName = styled.span<NodeNameProps>`
  ${({theme, isSelected, isBold}) => {
    const colors = theme.colors.flowNodeInstancesTree.bar.nodeName;
    const opacity = theme.opacity.flowNodeInstancesTree.bar.nodeName;

    return css`
      margin-left: 5px;
      padding-left: 5px;
      border-left: 1px solid
        ${isSelected ? colors.selected.borderColor : theme.colors.borderColor};
      color: ${theme.colors.text02};
      opacity: ${isSelected ? opacity.selected : opacity.default};

      ${isBold
        ? css`
            font-weight: bold;
          `
        : ''};
    `;
  }}
`;

export {NodeIcon, Container, NodeName};
