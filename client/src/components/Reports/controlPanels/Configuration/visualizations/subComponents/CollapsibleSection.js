/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import classnames from 'classnames';

import {Button, Icon} from 'components';

import './CollapsibleSection.scss';

export default function CollapsibleSection({
  children,
  sectionTitle,
  isSectionOpen,
  toggleSectionOpen,
}) {
  return (
    <section className={classnames('CollapsibleSection', {collapsed: !isSectionOpen})}>
      <Button className="sectionTitle" onClick={toggleSectionOpen}>
        {sectionTitle}
        <span className={classnames('sectionToggle', {open: isSectionOpen})}>
          <Icon type="down" />
        </span>
      </Button>
      <div>{children}</div>
    </section>
  );
}
