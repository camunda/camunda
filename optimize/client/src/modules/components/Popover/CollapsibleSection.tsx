/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MouseEvent, ReactNode, createContext, useContext} from 'react';
import classnames from 'classnames';
import {ChevronDown} from '@carbon/icons-react';

import './CollapsibleSection.scss';

interface CollapsibleSectionProps {
  children: ReactNode;
  sectionTitle: string;
  isSectionOpen: boolean;
  toggleSectionOpen: (evt: MouseEvent<HTMLButtonElement>) => void;
}

export const CollapsibleSectionContext = createContext<{
  calculateDialogStyle?: () => void;
} | null>(null);

export default function CollapsibleSection({
  children,
  sectionTitle,
  isSectionOpen,
  toggleSectionOpen,
}: CollapsibleSectionProps) {
  const collapsibleSectionData = useContext(CollapsibleSectionContext);
  if (collapsibleSectionData == null) {
    throw Error('A Collapsible Section must have Popover parent');
  }

  const handleClick = (evt: MouseEvent<HTMLButtonElement>) => {
    toggleSectionOpen(evt);

    setTimeout(() => {
      // We call calculateDialogStyle after toggling the section to ensure the Popover recalculates
      // its position and size based on the newly updated DOM layout (e.g., expanded content height).
      // The timeout ensures the DOM update from React's state change has taken effect.
      collapsibleSectionData.calculateDialogStyle?.();
    }, 0);
  };

  return (
    <section className={classnames('CollapsibleSection', {collapsed: !isSectionOpen})}>
      <button type="button" className="sectionTitle" onClick={handleClick}>
        {sectionTitle}
        <span className={classnames('sectionToggle', {open: isSectionOpen})}>
          <ChevronDown />
        </span>
      </button>
      {children}
    </section>
  );
}
