/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense} from 'react';
import {Switch} from '@carbon/react';
import {ViewSwitcher} from './styled';

type ModalView = 'preview' | 'source';

type ModalViewProps = {
  view?: ModalView;
  onViewChange?: (view: ModalView) => void;
};

const RichTextEditor = lazy(async () => {
  const [{loadMonaco}, {RichTextEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/RichTextEditor'),
  ]);

  loadMonaco();

  return {default: RichTextEditor};
});

const ModalViewSwitcher = ({
  view,
  onChange,
}: {
  view: ModalView;
  onChange: (view: ModalView) => void;
}) => (
  <ViewSwitcher
    size="sm"
    onChange={({name}) => {
      if (name === 'preview' || name === 'source') {
        onChange(name);
      }
    }}
    selectedIndex={view === 'preview' ? 0 : 1}
  >
    <Switch name="preview" text="Preview" />
    <Switch name="source" text="Source" />
  </ViewSwitcher>
);

const SourceEditor = ({
  value,
  language = 'json',
  height,
}: {
  value: string;
  language?: 'json' | 'markdown';
  height?: string;
}) => (
  <Suspense fallback={<span>Loading source…</span>}>
    <RichTextEditor
      value={value}
      readOnly
      language={language}
      height={height}
    />
  </Suspense>
);

export {ModalViewSwitcher, SourceEditor};
export type {ModalView, ModalViewProps};
