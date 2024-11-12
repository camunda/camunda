/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IconButton} from '@carbon/react';
import {Add, Subtract, CenterCircle} from '@carbon/icons-react';

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
        icon: CenterCircle,
        description: t('common.zoomControls.resetZoom').toString(),
        key: 'reset',
      },
      {
        onClick: () => zoom(ZOOM_STEP_SIZE),
        className: 'zoomIn',
        icon: Add,
        description: t('common.zoomControls.zoomIn').toString(),
        key: 'zoomIn',
      },
      {
        onClick: () => zoom(-ZOOM_STEP_SIZE),
        className: 'zoomOut',
        icon: Subtract,
        description: t('common.zoomControls.zoomOut').toString(),
        key: 'zoomOut',
      },
    ],
    [fit, zoom]
  );

  return (
    <div className="ZoomControls">
      {buttons.map(({onClick, className, icon: Icon, description, key}) => (
        <IconButton
          key={key}
          size="sm"
          kind="tertiary"
          className={className}
          onClick={onClick}
          label={description}
          align="left"
        >
          <Icon />
        </IconButton>
      ))}
    </div>
  );
}
