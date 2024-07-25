/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MouseEvent, ReactNode} from 'react';
import classnames from 'classnames';
import {ChevronDown} from '@carbon/icons-react';

import './CollapsibleSection.scss';

interface CollapsibleSectionProps {
  children: ReactNode;
  sectionTitle: string;
  isSectionOpen: boolean;
  toggleSectionOpen: (evt: MouseEvent<HTMLButtonElement>) => void;
}

export default function CollapsibleSection({
  children,
  sectionTitle,
  isSectionOpen,
  toggleSectionOpen,
}: CollapsibleSectionProps) {
  return (
    <section className={classnames('CollapsibleSection', {collapsed: !isSectionOpen})}>
      <button type="button" className="sectionTitle" onClick={toggleSectionOpen}>
        {sectionTitle}
        <span className={classnames('sectionToggle', {open: isSectionOpen})}>
          <ChevronDown />
        </span>
      </button>
      {children}
    </section>
  );
}
