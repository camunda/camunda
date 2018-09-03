import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Message = styled.div`
  display: flex;
  justify-content: left;
  align-items: center;

  line-height: 0px;
  padding: 6px 16px;

  border-radius: 11.5px;
  background: rgba(200, 137, 254, 0.5);
`;

export const Text = themed(styled.div`
  margin-left: 11px;

  font-size: 13px;
  font-family: IBMPlexSans;
  font-weight: normal;
  font-style: normal;

  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark04
  })};
`);

export const Dot = themed(styled.div`
  top: 1px;
  width: 9px;
  height: 9px;
  border-radius: 50%;

  background-color: ${themeStyle({
    dark: Colors.darkLinkVisited,
    light: Colors.lightLinkVisited
  })};
`);
