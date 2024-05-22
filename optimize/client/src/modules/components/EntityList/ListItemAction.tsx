/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {OverflowMenu, OverflowMenuItem, IconButton} from '@carbon/react';

import {Action} from './EntityList';

import './ListItemAction.scss';

interface ListItemActionProps {
  actions?: Action[];
  showInlineIconButtons?: boolean;
}

export default function ListItemAction({actions = [], showInlineIconButtons}: ListItemActionProps) {
  if (!actions || actions.length === 0) {
    return null;
  }

  if (showInlineIconButtons) {
    return actions.map(({icon, action, text}, idx) => (
      <IconButton
        key={idx}
        className="ListItemAction"
        label={text}
        kind="ghost"
        onClick={() => action()}
        size="sm"
      >
        {icon}
      </IconButton>
    ));
  }

  return (
    <OverflowMenu size="sm" flipped>
      {actions.map(({action, icon, text}, idx) => (
        <OverflowMenuItem
          className="ListItemSingleAction"
          key={idx}
          onClick={action}
          itemText={
            <>
              {icon}
              {text}
            </>
          }
        />
      ))}
    </OverflowMenu>
  );
}
