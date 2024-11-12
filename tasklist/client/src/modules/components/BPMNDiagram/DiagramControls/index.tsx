/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IconButton} from '@carbon/react';
import {Add, CenterCircle, Subtract} from '@carbon/react/icons';
import styles from './styles.module.scss';
import {useTranslation} from 'react-i18next';

type Props = {
  handleZoomReset: () => void;
  handleZoomIn: () => void;
  handleZoomOut: () => void;
};

const DiagramControls: React.FC<Props> = ({
  handleZoomReset,
  handleZoomIn,
  handleZoomOut,
}) => {
  const {t} = useTranslation();

  return (
    <div className={styles.container}>
      <IconButton
        className={styles.zoomReset}
        size="sm"
        kind="tertiary"
        align="left"
        label={t('taskDetailsResetDiagramZoom')}
        aria-label={t('taskDetailsResetDiagramZoom')}
        onClick={handleZoomReset}
      >
        <CenterCircle />
      </IconButton>
      <IconButton
        className={styles.zoomIn}
        size="sm"
        kind="tertiary"
        align="left"
        label={t('taskDetailsZoomInDiagram')}
        aria-label={t('taskDetailsZoomInDiagram')}
        onClick={handleZoomIn}
      >
        <Add />
      </IconButton>
      <IconButton
        className={styles.zoomOut}
        size="sm"
        kind="tertiary"
        align="left"
        label={t('taskDetailsZoomOutDiagram')}
        aria-label={t('taskDetailsZoomOutDiagram')}
        onClick={handleZoomOut}
      >
        <Subtract />
      </IconButton>
    </div>
  );
};

export {DiagramControls};
