/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from '@carbon/react';
import {ZoomIn, ZoomOut, ZoomReset} from '@carbon/icons-react';

import {t} from 'translation';

import './ZoomControls.scss';
import {useMemo} from 'react';

const ZOOM_STEP_SIZE = 0.1;

interface ZoomControlsProps {
  fit: () => void;
  zoom: (stepSize: number) => void;
}

export default function ZoomControls({fit, zoom}: ZoomControlsProps) {
  const buttons = useMemo(
    () => [
      {
        onClick: fit,
        className: 'reset',
        icon: ZoomReset,
        description: t('common.zoomControls.resetZoom').toString(),
      },
      {
        onClick: () => zoom(ZOOM_STEP_SIZE),
        className: 'zoomIn',
        icon: ZoomIn,
        description: t('common.zoomControls.zoomIn').toString(),
      },
      {
        onClick: () => zoom(-ZOOM_STEP_SIZE),
        className: 'zoomOut',
        icon: ZoomOut,
        description: t('common.zoomControls.zoomOut').toString(),
      },
    ],
    [fit, zoom]
  );

  return (
    <div className="ZoomControls">
      {buttons.map(({onClick, className, icon, description}) => (
        <Button
          size="sm"
          kind="ghost"
          className={className}
          onClick={onClick}
          hasIconOnly
          iconDescription={description}
          renderIcon={icon}
          tooltipAlignment="end"
        />
      ))}
    </div>
  );
}
