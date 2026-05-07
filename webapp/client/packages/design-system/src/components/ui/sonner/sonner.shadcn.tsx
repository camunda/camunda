/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Wraps the `sonner` toast library with shadcn styling. Vanilla shadcn
 * uses `next-themes`'s `useTheme()` to sync; we replace that with a
 * tiny `MutationObserver` on the document's `dark` class — set by our
 * `ThemeProvider` — so the Toaster restyles automatically when the
 * theme flips.
 */

import * as React from 'react';
import {
  CircleCheckIcon,
  InfoIcon,
  Loader2Icon,
  OctagonXIcon,
  TriangleAlertIcon,
} from 'lucide-react';
import {Toaster as Sonner, type ToasterProps, toast} from 'sonner';

function useThemeMode(): 'light' | 'dark' {
  const [isDark, setIsDark] = React.useState(() =>
    typeof document !== 'undefined'
      ? document.documentElement.classList.contains('dark')
      : false,
  );

  React.useEffect(() => {
    const html = document.documentElement;
    const observer = new MutationObserver(() => {
      setIsDark(html.classList.contains('dark'));
    });
    observer.observe(html, {attributes: true, attributeFilter: ['class']});
    return () => observer.disconnect();
  }, []);

  return isDark ? 'dark' : 'light';
}

const Toaster = ({...props}: ToasterProps) => {
  const theme = useThemeMode();

  return (
    <Sonner
      theme={theme}
      className="toaster group"
      icons={{
        success: <CircleCheckIcon className="size-4" />,
        info: <InfoIcon className="size-4" />,
        warning: <TriangleAlertIcon className="size-4" />,
        error: <OctagonXIcon className="size-4" />,
        loading: <Loader2Icon className="size-4 animate-spin" />,
      }}
      style={
        {
          '--normal-bg': 'var(--popover)',
          '--normal-text': 'var(--popover-foreground)',
          '--normal-border': 'var(--border)',
          '--border-radius': 'var(--radius)',
        } as React.CSSProperties
      }
      {...props}
    />
  );
};

export {Toaster, toast};
