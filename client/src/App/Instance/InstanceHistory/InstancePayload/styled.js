import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const InstancePayload = themed(styled.div`
  flex: 1;
  font-size: 14px;

  overflow: auto;

  position: relative;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};

  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.8)',
    light: 'rgba(98, 98, 110, 0.8)'
  })};
`);

export const PayloadContent = styled.div`
  position: absolute;
  height: 100%;
  width: 100%;
  display: flex;
  align-items: center;
`;

export const Placeholder = themed(styled.span`
  text-align: center;
  top: 0;
  left: 0;
  width: 100%;
  font-size: 16px;
  color: ${themeStyle({
    dark: '#dedede',
    light: Colors.uiLight06
  })};
`);

export const Table = themed(styled.table`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  border-spacing: 0;
  border-collapse: collapse;
`);

export const TH = themed(styled.th`
  font-style: italic;
  font-weight: normal;
  text-align: left;
  padding-left: 17px;
  height: 31px;
`);

export const TD = themed(styled.td`
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)'
  })};
  font-weight: ${props => (props.isBold ? 'bold' : 'normal')};
  padding-left: 17px;
  height: 32px;
`);

export const TR = themed(styled.tr`
  border-width: 1px 0;
  border-style: solid;
  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05
  })};

  &:first-child {
    border-top: none;
  }
`);
