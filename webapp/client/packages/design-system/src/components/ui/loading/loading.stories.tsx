/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Loading as CarbonLoading} from './loading.carbon';
import {Spinner} from './loading.shadcn';

const meta: Meta = {
  title: 'UI/Loading',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (large)</div>
        <div className="relative h-32">
          <CarbonLoading withOverlay={false} />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (Spinner with custom size)
        </div>
        <div className="flex h-32 items-center justify-center">
          <Spinner className="size-12" />
        </div>
      </div>
    </div>
  ),
};

export const Small: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (small)</div>
        <div className="relative h-12">
          <CarbonLoading withOverlay={false} small />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (default size)</div>
        <div className="flex h-12 items-center justify-center">
          <Spinner />
        </div>
      </div>
    </div>
  ),
};

export const WithOverlay: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (<code>withOverlay</code>)
        </div>
        <div className="relative h-48 bg-muted">
          <CarbonLoading withOverlay />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (compose absolute-positioned overlay + Spinner)
        </div>
        <div className="relative h-48 bg-muted">
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm">
            <Spinner className="size-12" />
          </div>
        </div>
      </div>
    </div>
  ),
};

export const Centered: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <div className="relative h-48 border">
          <CarbonLoading withOverlay={false} />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (centered with text)
        </div>
        <div className="flex h-48 items-center justify-center border">
          <div className="flex flex-col items-center gap-3">
            <Spinner className="size-8" />
            <span className="text-sm text-muted-foreground">Loading…</span>
          </div>
        </div>
      </div>
    </div>
  ),
};
