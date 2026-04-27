/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ReadOnlyEditorContainer,
  ReadOnlyEditorContent,
  ReadOnlyEditorWrapper,
  CopyIcon,
  CopyLoadingIcon,
} from './styled';
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
      } catch {
        setIsCopying(false);
        notificationsStore.displayNotification({
          kind: 'error',
          title: `Failed to fetch full ${label}`,
          isDismissable: true,
        });
        return;
      }
      setIsCopying(false);
    }

    try {
      await navigator.clipboard.writeText(valueToCopy);

      notificationsStore.displayNotification({
        kind: 'success',
        title: `Copied ${label} to clipboard`,
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
    <ReadOnlyEditorContainer>
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
          aria-disabled={isReadOnly && isCopying ? true : undefined}
          onClick={isReadOnly ? handleCopy : undefined}
          onKeyDown={isReadOnly ? handleCopyKeyDown : undefined}
        >
          {value || placeholder}
        </ReadOnlyEditorContent>
        {renderButton && renderButton()}
      </ReadOnlyEditorWrapper>
      {isReadOnly && !isCopying && (
        <CopyIcon data-testid="copy-icon-indicator" aria-hidden="true">
          <Copy size={16} />
        </CopyIcon>
      )}
      {isReadOnly && isCopying && (
        <CopyLoadingIcon
          data-testid="copy-loading-indicator"
          aria-hidden="true"
          status="active"
        />
      )}
    </ReadOnlyEditorContainer>
  );
};

export {ReadOnlyEditor};
