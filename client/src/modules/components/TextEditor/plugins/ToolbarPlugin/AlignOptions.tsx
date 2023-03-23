/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ElementFormatType, FORMAT_ELEMENT_COMMAND, LexicalEditor} from 'lexical';

import {Dropdown} from 'components';
import {t} from 'translation';

export default function AlignOptions({
  disabled,
  editor,
}: {
  disabled?: boolean;
  editor: LexicalEditor;
}) {
  const onAlign = (alignment: ElementFormatType) => () => {
    editor.dispatchCommand(FORMAT_ELEMENT_COMMAND, alignment);
  };

  const ALIGN_TYPES: ElementFormatType[] = ['left', 'center', 'right'];

  return (
    <Dropdown
      small
      disabled={disabled}
      label={t('textEditor.toolbar.align.label')}
      className="AlignOptions"
    >
      {ALIGN_TYPES.map((type) => (
        <Dropdown.Option key={type} onClick={onAlign(type)}>
          {t(`textEditor.toolbar.align.${type}`)}
        </Dropdown.Option>
      ))}
    </Dropdown>
  );
}
