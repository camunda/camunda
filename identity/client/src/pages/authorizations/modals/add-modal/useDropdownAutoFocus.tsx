/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useEffect, useRef } from "react";

/**
 * Workaround to auto-focus Carbon Dropdown components.
 * Needed because of this bug: https://github.com/carbon-design-system/carbon/issues/12970
 */
export const useDropdownAutoFocus = (open: boolean) => {
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open && dropdownRef.current) {
      const triggerButton = dropdownRef.current.querySelector(
        '[role="combobox"]',
      ) as HTMLElement;
      if (triggerButton) {
        triggerButton.focus();
      }
    }
  }, [open]);

  const DropdownAutoFocus = useCallback(
    ({ children }: { children: React.ReactNode }) => {
      return <div ref={dropdownRef}>{children}</div>;
    },
    [],
  );

  return { DropdownAutoFocus };
};
