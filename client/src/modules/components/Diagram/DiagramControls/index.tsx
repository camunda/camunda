/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactComponent as DiagramReset} from 'modules/components/Icon/diagram-reset.svg';
import {ReactComponent as Plus} from 'modules/components/Icon/plus.svg';
import {ReactComponent as Minus} from 'modules/components/Icon/minus.svg';

import * as Styled from './styled';

type Props = {
  handleZoomReset: (...args: any[]) => any;
  handleZoomIn: (...args: any[]) => any;
  handleZoomOut: (...args: any[]) => any;
};

const DiagramControls: React.FC<Props> = ({
  handleZoomReset,
  handleZoomIn,
  handleZoomOut,
}) => {
  return (
    <Styled.DiagramControls>
      <Styled.ZoomReset onClick={handleZoomReset} title="Reset diagram zoom">
        <DiagramReset />
      </Styled.ZoomReset>
      <Styled.ZoomIn onClick={handleZoomIn} title="Zoom in diagram">
        <Plus />
      </Styled.ZoomIn>
      <Styled.ZoomOut onClick={handleZoomOut} title="Zoom out diagram">
        <Minus />
      </Styled.ZoomOut>
    </Styled.DiagramControls>
  );
};

export default DiagramControls;
