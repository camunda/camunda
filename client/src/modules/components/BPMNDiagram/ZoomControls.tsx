/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button, Icon} from 'components';
import './ZoomControls.scss';

const ZOOM_STEP_SIZE = 0.1;

interface ZoomControlsProps {
  fit: () => void;
  zoom: (stepSize: number) => void;
}

export default function ZoomControls({fit, zoom}: ZoomControlsProps) {
  return (
    <div className="ZoomControls">
      <Button className="reset" onClick={fit}>
        <Icon type="diagram-reset" />
      </Button>
      <Button className="zoomIn" onClick={() => zoom(ZOOM_STEP_SIZE)}>
        <Icon type="plus" />
      </Button>
      <Button className="zoomOut" onClick={() => zoom(-ZOOM_STEP_SIZE)}>
        <Icon type="minus" />
      </Button>
    </div>
  );
}
