import styled from 'styled-components';
import {Link} from 'react-router-dom';

import {Colors, themed, themeStyle} from 'modules/theme';

export const InstancesList = styled.div`
  flex-grow: 1;
  position: relative;
`;

export const TableContainer = styled.div`
  position: absolute;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;
`;

export const SelectionStatusIndicator = themed(styled.div`
  display: inline-block;
  height: 37px;
  width: 9px;
  ${({selected}) =>
    selected &&
    `background-color: ${Colors.selections};`} vertical-align: bottom;
  margin-left: -5px;
  margin-right: 11px;

  border-right: 1px solid
    ${themeStyle({dark: Colors.uiDark04, light: Colors.uiLight05})};
`);

export const CheckAll = styled.div`
  display: inline-block;
  margin-left: 16px;
  margin-right: 28px;
`;

export const Selection = styled.div`
  position: relative;
  display: flex;
  align-items: center;

  & * {
    top: 0px;
  }
`;

export const InstanceAnchor = themed(styled(Link)`
  text-decoration: underline;
  color: ${themeStyle({
    dark: Colors.darkLinkBlue,
    light: Colors.lightLinkBlue
  })};
`);

export const WorkflowName = styled.span`
  margin-left: 7px;
`;
