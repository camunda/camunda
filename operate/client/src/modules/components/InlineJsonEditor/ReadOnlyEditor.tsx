/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReadOnlyEditorContent, ReadOnlyEditorWrapper} from './styled';
import {useCallback, useMemo} from 'react';
import {EDITOR_MAX_LINES} from './constants';
import {notificationsStore} from 'modules/stores/notifications';

interface Props {
  value: string;
  placeholder: string;
  isReadOnly: boolean;
  fieldError?: string;
  height: number;
  maxLines: number;
  label?: string;
  'data-testid'?: string;
  renderButton?: () => React.ReactNode;
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
}) => {
  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(value).then(
      () => {
        notificationsStore.displayNotification({
          kind: 'success',
          title: `${label} copied to clipboard`,
          isDismissable: true,
        });
      },
      () => {
        notificationsStore.displayNotification({
          kind: 'error',
          title: `Failed to copy ${label} to clipboard`,
          isDismissable: true,
        });
      },
    );
  }, [value, label]);

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
      data-testid={
        dataTestId ? `${dataTestId}-readonly` : 'json-editor-readonly'
      }
      $height={height}
      $empty={value === ''}
      $editMode={!isReadOnly}
      $scrollable={isScrollable}
      $invalid={!!fieldError}
    >
      <ReadOnlyEditorContent
        tabIndex={0}
        role="button"
        aria-label={isReadOnly ? `Copy ${label}` : undefined}
        onClick={isReadOnly ? handleCopy : undefined}
        onKeyDown={isReadOnly ? handleCopyKeyDown : undefined}
      >
        {value || placeholder}
      </ReadOnlyEditorContent>
      {renderButton && renderButton()}
    </ReadOnlyEditorWrapper>
  );
};

export {ReadOnlyEditor};
