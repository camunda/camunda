import styled from 'styled-components';
import {Colors, themeStyle} from 'modules/theme';

export const Popover = styled.div`
  &:before,
  &:after {
    position: absolute;
    border: solid transparent;
    content: ' ';
    pointer-events: none;
    bottom: 50%;
    pointer-events: none;
    bottom: 100%;
  }

  &:before {
    border-width: 9px;
    border-bottom-color: ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.6)',
      light: 'rgba(0, 0, 0, 0.2)'
    })};
    left: 20px;
  }

  &:after {
    border-width: 8px;
    border-bottom-color: ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight02
    })};
    left: 21px;
  }

  background-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02
  })};

  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};

  font-size: 12px;
  border-radius: 3px;

  box-shadow: 0 0 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.6)',
      light: 'rgba(0, 0, 0, 0.2)'
    })};

  padding: 11px;
  position: relative;
`;

export const Metadata = styled.table`
  margin: 0;
  padding: 0;
  font-weight: 600;
`;

export const MetadataRow = styled.tr`
  & td {
    white-space: nowrap;
  }
  & td:first-child {
    text-align: right;
    font-weight: normal;
    margin-right: 6px;
  }
`;
