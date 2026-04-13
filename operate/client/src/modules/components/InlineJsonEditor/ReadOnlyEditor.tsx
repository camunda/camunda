/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReadOnlyEditorContent, ReadOnlyEditorWrapper, CopyIcon} from './styled';
import {useCallback, useMemo, useState} from 'react';
import {EDITOR_MAX_LINES} from './constants';
import {notificationsStore} from 'modules/stores/notifications';
import {Copy} from '@carbon/react/icons';

interface Props {
  value: string;
  placeholder: string;
  isReadOnly: boolean;
  fieldError?: string;
  height: number;
  maxLines?: number;
  label?: string;
  'data-testid'?: string;
  renderButton?: () => React.ReactNode;
  onCopy?: () => Promise<string>;
}

const ReadOnlyEditor: React.FC<Props> = ({
  value,
  placeholder,
  isReadOnly,
  maxLines = EDITOR_MAX_LINES,
  fieldError,
  label = 'value',
  renderButton,
  height,
  'data-testid': dataTestId,
  onCopy,
}) => {
  const [isCopying, setIsCopying] = useState(false);

  const handleCopy = useCallback(async () => {
    if (isCopying) {
      return;
    }

    let valueToCopy = value;

    if (onCopy) {
      setIsCopying(true);
      try {
        valueToCopy = await onCopy();
      } finally {
        setIsCopying(false);
      }
    }

    try {
      await navigator.clipboard.writeText(valueToCopy);
      notificationsStore.displayNotification({
        kind: 'success',
        title: `${label} copied to clipboard`,
        isDismissable: true,
      });
    } catch {
      notificationsStore.displayNotification({
        kind: 'error',
        title: `Failed to copy ${label} to clipboard`,
        isDismissable: true,
      });
    }
  }, [value, label, onCopy, isCopying]);

  const handleCopyKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        handleCopy();
      }
    },
    [handleCopy],
  );

  const lineCount = useMemo(() => {
    return Math.max(1, (value || '').split('\n').length);
  }, [value]);

  const isScrollable = useMemo(() => {
    return lineCount > maxLines;
  }, [lineCount, maxLines]);

  return (
    <ReadOnlyEditorWrapper
      $height={height}
      $empty={value === ''}
      $editMode={!isReadOnly}
      $scrollable={isScrollable}
      $invalid={!!fieldError}
    >
      <ReadOnlyEditorContent
        data-testid={
          dataTestId ? `${dataTestId}-readonly` : 'json-editor-readonly'
        }
        tabIndex={0}
        role={isReadOnly ? 'button' : undefined}
        aria-label={isReadOnly ? `Copy ${label}` : undefined}
        aria-busy={isCopying || undefined}
        onClick={isReadOnly && !isCopying ? handleCopy : undefined}
        onKeyDown={isReadOnly && !isCopying ? handleCopyKeyDown : undefined}
      >
        {value || placeholder}
      </ReadOnlyEditorContent>
      {isReadOnly && (
        <CopyIcon
          data-testid="copy-icon-indicator"
          aria-hidden="true"
          $isCopying={isCopying}
        >
          <Copy size={16} />
        </CopyIcon>
      )}
      {renderButton && renderButton()}
    </ReadOnlyEditorWrapper>
  );
};

export {ReadOnlyEditor};
