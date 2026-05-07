/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';
import {Check, Copy} from 'lucide-react';

import {cn} from '../../../lib/utils';
import {Button} from '../button/button.shadcn';

type CodeSnippetType = 'inline' | 'single' | 'multi';

type CodeSnippetProps = Omit<React.ComponentProps<'div'>, 'onClick'> & {
  type?: CodeSnippetType;
  copyText?: string;
  hideCopyButton?: boolean;
  disabled?: boolean;
  feedback?: string;
  feedbackTimeout?: number;
  copyButtonDescription?: string;
  wrapText?: boolean;
  maxCollapsedNumberOfRows?: number;
  showMoreText?: string;
  showLessText?: string;
  onClick?: (event: React.MouseEvent) => void;
  children?: React.ReactNode;
};

function useClipboard(timeout: number) {
  const [copied, setCopied] = React.useState(false);
  const timer = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  React.useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current);
  }, []);

  const copy = React.useCallback(
    async (text: string) => {
      try {
        await navigator.clipboard.writeText(text);
      } catch {
        // navigator.clipboard may be unavailable; fail silently rather than crash.
      }
      setCopied(true);
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => setCopied(false), timeout);
    },
    [timeout],
  );

  return {copied, copy};
}

function getInnerText(children: React.ReactNode): string {
  if (typeof children === 'string') return children;
  if (typeof children === 'number') return String(children);
  if (Array.isArray(children)) return children.map(getInnerText).join('');
  if (React.isValidElement(children)) {
    const childChildren = (children.props as {children?: React.ReactNode})
      .children;
    return getInnerText(childChildren);
  }
  return '';
}

function CodeSnippet({
  type = 'single',
  copyText,
  hideCopyButton,
  disabled,
  feedback = 'Copied!',
  feedbackTimeout = 2000,
  copyButtonDescription = 'Copy to clipboard',
  wrapText,
  maxCollapsedNumberOfRows = 15,
  showMoreText = 'Show more',
  showLessText = 'Show less',
  onClick,
  children,
  className,
  ...props
}: CodeSnippetProps) {
  const text = copyText ?? getInnerText(children);
  const {copied, copy} = useClipboard(feedbackTimeout);
  const [expanded, setExpanded] = React.useState(false);

  const handleCopy = (event: React.MouseEvent) => {
    onClick?.(event);
    if (!disabled) copy(text);
  };

  if (type === 'inline') {
    return (
      <code
        data-slot="code-snippet"
        data-type="inline"
        className={cn(
          'inline-flex max-w-full items-center gap-1.5 rounded bg-muted px-1.5 py-0.5 font-mono text-[0.85em]',
          !hideCopyButton && 'cursor-pointer hover:bg-muted/80',
          disabled && 'pointer-events-none opacity-50',
          className,
        )}
        onClick={!hideCopyButton ? handleCopy : undefined}
        title={!hideCopyButton ? copyButtonDescription : undefined}
        {...(props as React.ComponentProps<'code'>)}
      >
        <span className="truncate">{children}</span>
        {!hideCopyButton && (
          <span aria-hidden className="shrink-0 text-muted-foreground">
            {copied ? (
              <Check className="size-3" />
            ) : (
              <Copy className="size-3" />
            )}
          </span>
        )}
        <span className="sr-only" aria-live="polite">
          {copied ? feedback : ''}
        </span>
      </code>
    );
  }

  const lineCount =
    typeof text === 'string' ? text.split('\n').length : 1;
  const isMulti = type === 'multi';
  const showExpand = isMulti && lineCount > maxCollapsedNumberOfRows;
  const collapsedHeight = `${maxCollapsedNumberOfRows * 1.5}rem`;

  return (
    <div
      data-slot="code-snippet"
      data-type={type}
      className={cn(
        'group relative rounded-md border bg-muted/40 font-mono text-sm',
        disabled && 'pointer-events-none opacity-50',
        className,
      )}
      {...props}
    >
      <pre
        className={cn(
          'm-0 overflow-auto px-3 py-2 pr-12',
          isMulti ? '' : 'overflow-hidden whitespace-nowrap',
          wrapText && 'whitespace-pre-wrap break-words',
        )}
        style={
          showExpand && !expanded
            ? {maxHeight: collapsedHeight, overflow: 'hidden'}
            : undefined
        }
      >
        <code>{children}</code>
      </pre>

      {!hideCopyButton && (
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="absolute top-1.5 right-1.5 size-7"
          onClick={handleCopy}
          aria-label={copyButtonDescription}
          disabled={disabled}
        >
          {copied ? (
            <Check className="size-4" />
          ) : (
            <Copy className="size-4" />
          )}
        </Button>
      )}

      {showExpand && (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="block w-full border-t border-border px-3 py-1.5 text-left text-xs text-primary hover:bg-muted/60"
        >
          {expanded ? showLessText : showMoreText}
        </button>
      )}

      <span className="sr-only" aria-live="polite">
        {copied ? feedback : ''}
      </span>
    </div>
  );
}

export {CodeSnippet};
export type {CodeSnippetProps, CodeSnippetType};
