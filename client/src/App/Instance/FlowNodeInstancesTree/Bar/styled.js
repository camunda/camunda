import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import BasicFlowNodeIcon from 'modules/components/FlowNodeIcon';

export const NodeIcon = styled(BasicFlowNodeIcon)``;

export const Bar = themed(styled.div`
  display: flex;
  height: 28px;
  font-size: 13px;
  min-width: 200px;
  align-items: center;
  flex-grow: 1;
  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05
  })};
  border-width: 0px 1px 1px 1px;
  border-style: solid;
  background: ${({isSelected}) =>
    isSelected
      ? Colors.selections
      : themeStyle({
          dark: Colors.darkItemEven,
          light: Colors.lightItemEven
        })};
`);

export const NodeName = themed(styled.span`
  border-left: 1px solid
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  margin-left: 5px;
  padding-left: 5px;
  font-weight: ${({bold}) => (bold ? 'bold' : '')};
`);
