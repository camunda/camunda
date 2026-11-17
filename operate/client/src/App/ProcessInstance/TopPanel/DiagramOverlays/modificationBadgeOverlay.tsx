/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* eslint-disable react-refresh/only-export-components -- overlay modules intentionally co-locate their data hook, config, and renderer in a single file */

import {useMemo} from 'react';
import {MODIFICATIONS} from 'modules/bpmn-js/badgePositions';
import {useModificationsByElement} from 'modules/hooks/modifications';
import {ModificationBadgeOverlay as ModificationBadge} from 'modules/components/ModificationBadgeOverlay';
import type {OverlayData} from 'modules/bpmn-js/overlayTypes';
import type {DiagramOverlay} from './types';

const MODIFICATION_BADGE_OVERLAY_TYPE = 'modificationsBadge';

type ModificationBadgePayload = {
  newTokenCount: number;
  cancelledTokenCount: number;
};

const useModificationBadgeOverlaysData = (): OverlayData[] => {
  const modificationsByElement = useModificationsByElement();

  return useMemo(
    () =>
      Object.entries(modificationsByElement).map(([elementId, tokens]) => ({
        elementId,
        type: MODIFICATION_BADGE_OVERLAY_TYPE,
        position: MODIFICATIONS,
        payload: {
          newTokenCount: tokens.newTokens,
          cancelledTokenCount: tokens.visibleCancelledTokens,
        } satisfies ModificationBadgePayload,
      })),
    [modificationsByElement],
  );
};

const ModificationBadgeOverlay: React.FC<{overlay: DiagramOverlay}> = ({
  overlay,
}) => {
  const payload = overlay.payload as ModificationBadgePayload;

  return (
    <ModificationBadge
      container={overlay.container}
      newTokenCount={payload.newTokenCount}
      cancelledTokenCount={payload.cancelledTokenCount}
    />
  );
};

const getModificationBadgeOverlayKey = (overlay: DiagramOverlay): string =>
  overlay.elementId;

export {
  MODIFICATION_BADGE_OVERLAY_TYPE,
  useModificationBadgeOverlaysData,
  ModificationBadgeOverlay,
  getModificationBadgeOverlayKey,
};
