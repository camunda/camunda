import styled from 'styled-components';

import Panel from 'modules/components/Panel';

import BasicExpandButton from 'modules/components/ExpandButton';

export const Filters = styled.div`
  display: flex;
  width: 320px;
  margin-right: 1px;
`;

export const ExpandButton = styled(BasicExpandButton)`
  position: absolute;
  right: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-right: none;
`;

export const ResetButtonContainer = styled(Panel.Footer)`
  display: flex;
  justify-content: center;
  height: 56px;
`;
