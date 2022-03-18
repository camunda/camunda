/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

type OverlayPosition = {
  top?: number;
  right?: number;
  bottom?: number;
  left?: number;
};

type OverlayType = {
  position: OverlayPosition;
  html: HTMLDivElement;
};

export type {OverlayType, OverlayPosition};
