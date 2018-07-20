import styled, {css} from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';
import {Down, Right} from 'modules/components/Icon';

export const ExpansionPanel = themed(styled.div`
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark04
  })};
`);

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
  opacity: ${themeStyle({
    dark: 0.9
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark04
  })};
`;

export const ExpandButton = styled.button`
  cursor: pointer;
  background: transparent;
  border: none;

  padding: 0;
  margin-right: 6px;
  top: 1px;
`;

export const DownIcon = themed(styled(Down)`
  ${iconStyle};
`);

export const RightIcon = themed(styled(Right)`
  ${iconStyle};
`);

export const Summary = themed(styled.div`
  display: flex;
  width: 100%;
  position: relative;
  padding-top: 7px;
  padding-bottom: 7px;

  font-weight: ${({bold}) => (!bold ? 'normal' : 'bold')};
`);

export const Details = styled.div`
  padding-left: 22px;
  display: ${({expanded}) => (expanded ? 'flex' : 'none')};
  flex-direction: column;
`;
