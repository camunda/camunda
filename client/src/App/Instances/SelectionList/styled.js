import styled from 'styled-components';
import {themed, themeStyle, Colors} from 'modules/theme';

const themedWith = (dark, light) => {
  return themeStyle({
    dark,
    light
  });
};

export const SelectionList = styled.div`
  padding: 20px 0 0 35px;
  overflow: auto;
`;

export const SelectionWrapper = styled.div`
  margin-bottom: 20px;
`;

export const NoSelectionWrapper = themed(styled.div`
  width: 443px;
  height: 94px;
  display: flex;
  justify-content: center;
  align-items: center;

  color: ${themedWith('#ffffff', Colors.uiLight06)};
  opacity: ${themedWith(0.9, 1)};
  background: ${themedWith(Colors.uiDark03, Colors.uiLight02)};
  border: 1px solid ${themedWith(Colors.uiDark04, Colors.uiLight05)};
  border-radius: 3px;

  text-align: center;
  font-size: 13px;
`);
