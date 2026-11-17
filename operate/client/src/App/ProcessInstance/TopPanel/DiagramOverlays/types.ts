/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * An overlay as it lives in the {@link diagramOverlaysStore} once the diagram has
 * mounted a container for it. This is what an overlay renderer receives.
 */
type DiagramOverlay = {
  payload?: unknown;
  container: HTMLElement;
  elementId: string;
  type: string;
};

export type {DiagramOverlay};
