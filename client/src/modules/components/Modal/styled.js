import styled from 'styled-components';

import {ReactComponent as CloseLarge} from 'modules/components/Icon/close-large.svg';
import Panel from 'modules/components/Panel';
import Button from 'modules/components/Button';
import {themed, Colors, themeStyle} from 'modules/theme';

export const ModalRoot = themed(styled.aside`
  z-index: 999;
  position: absolute;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: ${themeStyle({
    dark: 'rgba(0, 0, 0, 0.5)',
    light: 'rgba(255, 255, 255, 0.7)'
  })};
  box-shadow: 0 2px 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.5)',
      light: 'rgba(0, 0, 0, 0.5)'
    })};
`);

export const ModalContent = styled(Panel)`
  width: 80%;
  height: 90%;
`;

export const ModalHeader = styled(Panel.Header)`
  height: 56px;
  padding-top: 18px;
  padding-bottom: 19px;
  padding-left: 20px;
`;

export const CrossButton = styled.button`
  padding: 0;
  margin: 0;
  background: transparent;
  border: 0;
  position: absolute;
  right: 21px;
  top: 19px;
`;

export const CrossIcon = themed(styled(CloseLarge)`
  height: 16px;
  width: 16px;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
`);

export const ModalFooter = styled(Panel.Footer)`
  height: auto;
  display: flex;
  justify-content: flex-end;
`;

export const CloseButton = styled(Button)`
  background-color: ${Colors.selections};
  color: ${Colors.uiLight02};
`;
