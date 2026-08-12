/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { FC } from "react";

type EntitySearchDropdownProps<Entity> = {
  onSelect: (entity: Entity) => void;
  filter?: (entity: Entity) => boolean;
  placeholder: string;
  autoFocus?: boolean;
  errorTitle: string;
  retryLabel: string;
};

/**
 * Shared type definition for entity search dropdowns.
 * 
 * Implementations are kept separate by choice to avoid all entity searches having to function the same way.
 */
export type EntitySearchDropdown<Entity> = FC<
  EntitySearchDropdownProps<Entity>
>;
