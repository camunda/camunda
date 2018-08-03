import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

const backgroundColors = {
  filters: Colors.filtersAndWarnings,
  selections: Colors.selections,
  incidents: Colors.incidentsAndErrors,
  selectionHead: themeStyle({
    dark: '#ffffff',
    light: Colors.selections
  }),
  openSelectionHead: '#ffffff',
  comboSelection: Colors.selections
};

const colors = {
  filters: Colors.uiDark01,
  selectionHead: themeStyle({
    dark: Colors.uiDark03,
    light: '#ffffff'
  }),
  openSelectionHead: Colors.selections,
  comboSelection: '#ffffff'
};

export const Badge = themed(styled.div`
  display: inline-block;
  height: 17px;
  padding-left: 9px;
  padding-right: 9px;
  margin-left: 7px;

  border-radius: 8.5px;

  background: ${({type}) => backgroundColors[type] || '#a4a2a2'};
  color: ${({type}) => colors[type] || '#ffffff'};

  font-size: 12px;
  font-weight: 600;
  line-height: 17px;

  ${({type}) => type === 'comboSelection' && comboSelectionStyles};
`);

const comboSelectionStyles = css`
  position: relative;
  padding-left: 18px;
`;

export const Circle = themed(styled.div`
  /* Positioning  */
  position: absolute;

  /* Display & Box Model */
  display: flex;
  justify-content: center;
  align-items: center;
  width: 23px;
  left: -10px;
  bottom: -3px;
  padding: 1px 2px;
  border-style: solid;
  border-width: 2px;
  border-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
  border-radius: 50%;

  /* Color */
  color: white;
  background: ${Colors.selections};
`);
