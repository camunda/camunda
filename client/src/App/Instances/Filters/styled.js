import styled from 'styled-components';

import Panel from 'modules/components/Panel';
import BasicExpandButton from 'modules/components/ExpandButton';
import {themed, themeStyle} from 'modules/theme';

export const ExpandButton = styled(BasicExpandButton)`
  position: absolute;
  right: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-right: none;
`;

export const ResetButtonContainer = themed(styled(Panel.Footer)`
  display: flex;
  justify-content: center;
  height: 56px;
  box-shadow: ${themeStyle({
    dark: '0px -2px 4px 0px rgba(0,0,0,0.1)',
    light: '0px -1px 2px 0px rgba(0,0,0,0.1)'
  })};
  border-radius: 0;
`);

export const Filters = styled.div`
  padding: 20px 20px 0 20px;
  overflow: auto;
`;

export const Field = styled.div`
  padding: 10px 0;

  &:first-child {
    padding-top: 0;
  }
`;
