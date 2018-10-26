import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

import Spinner from 'modules/components/Spinner';

export const Actions = styled.div`
  display: flex;
  align-items: center;
`;

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
