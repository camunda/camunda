/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
