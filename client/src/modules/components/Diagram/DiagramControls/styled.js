import styled from 'styled-components';

import Button from 'modules/components/Button';

export const DiagramControls = styled.div`
  display: flex;
  flex-direction: column;
  position: absolute;
  right: 20px;
  bottom: 60px;
  z-index: 2;
  width: 28px;
`;

export const Box = styled(Button)`
  width: 100%;
  padding: 5px;
  height: 28px;
`;

export const ZoomReset = styled(Box)`
  border-radius: 3px;
  margin-bottom: 10px;
`;

export const ZoomIn = styled(Box)`
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
`;

export const ZoomOut = styled(Box)`
  border-top-left-radius: 0;
  border-top-right-radius: 0;
`;
