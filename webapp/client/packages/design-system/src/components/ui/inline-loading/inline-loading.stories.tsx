/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {CheckCircle2, XCircle} from 'lucide-react';
import {InlineLoading as CarbonInlineLoading} from './inline-loading.carbon';
import {Spinner} from './inline-loading.shadcn';

const meta: Meta = {
  title: 'UI/InlineLoading',
};
export default meta;

type Story = StoryObj;

export const Active: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonInlineLoading
          status="active"
          description="Saving changes…"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (compose Spinner + text)
        </div>
        <div className="flex items-center gap-2 text-sm">
          <Spinner />
          <span>Saving changes…</span>
        </div>
      </div>
    </div>
  ),
};

export const Finished: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonInlineLoading
          status="finished"
          description="Saved"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (no built-in finished state — render a check icon)
        </div>
        <div className="flex items-center gap-2 text-sm">
          <CheckCircle2 className="h-4 w-4 text-green-600" />
          <span>Saved</span>
        </div>
      </div>
    </div>
  ),
};

export const Error_: Story = {
  name: 'Error',
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonInlineLoading
          status="error"
          description="Could not save"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (render error icon manually)
        </div>
        <div className="flex items-center gap-2 text-sm text-destructive">
          <XCircle className="h-4 w-4" />
          <span>Could not save</span>
        </div>
      </div>
    </div>
  ),
};

export const Sizes: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonInlineLoading
          status="active"
          description="Loading…"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (resize via className)
        </div>
        <div className="flex flex-col gap-3 text-sm">
          <div className="flex items-center gap-2">
            <Spinner className="size-3" />
            <span>Small</span>
          </div>
          <div className="flex items-center gap-2">
            <Spinner />
            <span>Default (16px)</span>
          </div>
          <div className="flex items-center gap-2">
            <Spinner className="size-6" />
            <span className="text-base">Large</span>
          </div>
        </div>
      </div>
    </div>
  ),
};
