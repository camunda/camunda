/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {
  CAN_REDO_COMMAND,
  CAN_UNDO_COMMAND,
  COMMAND_PRIORITY_CRITICAL,
  REDO_COMMAND,
  UNDO_COMMAND,
} from 'lexical';
import {Undo, Redo} from '@carbon/icons-react';
import {mergeRegister} from '@lexical/utils';

import {Button} from 'components';
import {t} from 'translation';

export default function HistoryButtons({editor, disabled = false}) {
  const [canUndo, setCanUndo] = useState(false);
  const [canRedo, setCanRedo] = useState(false);

  useEffect(() => {
    return mergeRegister(
      editor.registerCommand(
        CAN_UNDO_COMMAND,
        (payload) => {
          setCanUndo(payload);
          return false;
        },
        COMMAND_PRIORITY_CRITICAL
      ),
      editor.registerCommand(
        CAN_REDO_COMMAND,
        (payload) => {
          setCanRedo(payload);
          return false;
        },
        COMMAND_PRIORITY_CRITICAL
      )
    );
  }, [editor]);

  const BUTTONS = {
    undo: {command: UNDO_COMMAND, Icon: Undo, disabled: !canUndo || disabled},
    redo: {command: REDO_COMMAND, Icon: Redo, disabled: !canRedo || disabled},
  };

  return (
    <>
      {Object.entries(BUTTONS).map(([key, {command, Icon, disabled}]) => (
        <Button
          key={key}
          small
          title={t(`textEditor.toolbar.history.${key}`)}
          disabled={disabled}
          onClick={() => {
            editor.dispatchCommand(command);
          }}
        >
          <Icon />
        </Button>
      ))}
    </>
  );
}
