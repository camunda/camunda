import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {HEADER_HEIGHT} from './../Header/styled';

export const Dashboard = styled.main`
  padding: 0 20px 38px;
  display: flex;
  flex-direction: column;
  height: calc(100vh - ${HEADER_HEIGHT}px);
`;

export const TitleWrapper = styled.div`
  display: flex;
  flex-grow: 1;
`;

export const Tile = themed(styled.div`
  // flex-parent
  display: flex;
  flex-direction: column;

  // flex-child
  flex-grow: 1;
  margin: 8px 8px 0 0;
  padding: 24px 12px 24px 32px;
  width: 50%;

  border-radius: 3px;
  border: solid 1px
    ${themeStyle({dark: Colors.uiDark04, light: Colors.uiLight05})};
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
  box-shadow: ${themeStyle({
    dark: '0 3px 6px 0 #000000',
    light: '0 2px 3px 0 rgba(0, 0, 0, 0.1)'
  })};

  &:last-child {
    margin-right: 0;
  }
`);

export const TileTitle = themed(styled.h2`
  margin: 0 0 14px;
  padding: 0;

  font-family: IBMPlexSans;
  font-size: 16px;
  font-weight: 600;
  line-height: 2;
  color: ${themeStyle({
    dark: '#fff',
    light: Colors.uiLight06
  })};
  opacity: ${themeStyle({
    dark: 0.9,
    light: 1
  })};
`);

export const TileContent = styled.div`
  overflow: auto;
  flex-grow: 1;
  position: relative;
`;
