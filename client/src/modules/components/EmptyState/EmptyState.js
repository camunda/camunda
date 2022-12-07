/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Icon} from 'components';

import './EmptyState.scss';

export default function EmptyState({icon, title, description, actions}) {
  return (
    <div className="EmptyState">
      <Icon type={icon} className="icon" />
      <div className="content">
        <div className="title">{title}</div>
        <div className="description">{description}</div>
        <div className="actions">{actions}</div>
      </div>
    </div>
  );
}
