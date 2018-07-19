import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Badge = themed(styled.div`
  display: inline-block;
  height: 17px;
  padding-left: 9px;
  padding-right: 9px;
  margin-left: 7px;

  background-color: ${({type}) => {
    switch (type) {
      case 'filters':
        return Colors.filtersAndWarnings;
      case 'selections':
        return Colors.selections;
      case 'incidents':
        return Colors.incidentsAndErrors;
      case 'selectionHead':
        return themeStyle({
          dark: '#ffffff',
          light: Colors.selections
        });
      case 'openSelectionHead':
        return '#ffffff';
      default:
        return '#a4a2a2';
    }
  }};
  color: ${({type}) => {
    switch (type) {
      case 'filters':
        return Colors.uiDark01;
      case 'selectionHead':
        return themeStyle({
          dark: Colors.uiDark03,
          light: '#ffffff'
        });
      case 'openSelectionHead':
        return Colors.selections;
      default:
        return '#ffffff';
    }
  }};

  font-size: 12px;
  font-weight: 600;
  line-height: 17px;

  border-radius: 8.5px;
`);
