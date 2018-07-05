import styled from 'styled-components';

import SplitPane from 'modules/components/SplitPane';
import Panel from 'modules/components/Panel';
import ExpandButton from 'modules/components/ExpandButton';

import {HEADER_HEIGHT} from './../Header/styled';

export const Filter = styled.div`
  display: flex;
  flex-direction: row;
  height: calc(100vh - ${HEADER_HEIGHT}px);

  /* prevents header dropdown to not go under the content */
  /* display: flex has z-index as well */
  z-index: 0;
`;

export const Left = styled.div`
  display: flex;
  width: 320px;
  margin-right: 1px;
`;

export const LeftExpandButton = styled(ExpandButton)`
  position: absolute;
  right: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-right: none;
`;

export const Center = styled(SplitPane)`
  width: 100%;
`;

export const Right = styled.div`
  width: 320px;
  display: flex;
`;

export const RightExpandButton = styled(ExpandButton)`
  position: absolute;
  left: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-left: none;
`;

export const SelectionHeader = styled(Panel.Header)`
  padding-left: 45px;
`;

export const FiltersFooter = styled(Panel.Footer)`
  display: flex;
  justify-content: center;
  height: 56px;
`;
