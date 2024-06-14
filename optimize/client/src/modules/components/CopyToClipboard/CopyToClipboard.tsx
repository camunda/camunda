/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps, ReactNode} from 'react';
import {Button} from '@carbon/react';

import {t} from 'translation';

interface CopyToClipboardProps extends Pick<ComponentProps<typeof Button>, 'kind' | 'disabled'> {
  children: ReactNode;
  value: string;
  onCopy: () => void;
}

export default function CopyToClipboard({
  children,
  value,
  disabled,
  onCopy,
  kind,
}: CopyToClipboardProps) {
  return (
    <Button
      className="CopyToClipboard"
      size="sm"
      onClick={(evt) => {
        evt.preventDefault();
        const input = document.createElement('input');
        input.value = value;
        input.style.opacity = '0';
        input.style.position = 'absolute';
        input.style.top = '0';

        document.body.appendChild(input);

        input.select();
        document.execCommand('Copy');

        document.body.removeChild(input);

        onCopy?.();
      }}
      disabled={disabled}
      kind={kind}
    >
      {children || t('common.copy')}
    </Button>
  );
}
