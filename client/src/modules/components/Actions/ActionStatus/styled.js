import styled from 'styled-components';

import Spinner from 'modules/components/Spinner';

import {Colors, themed, themeStyle} from 'modules/theme';

export const ActionSpinner = themed(styled(Spinner)`
  margin: 0 5px;
 border: 3px solid ${({selected}) =>
   themeStyle({
     dark: '#ffffff',
     light: selected ? Colors.selections : Colors.badge02
   })};
    border-right-color: transparent;
  }
`);
