/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {SkeletonText as AdapterSkeletonText} from './skeleton-text.adapter';
import {SkeletonText as CarbonSkeletonText} from './skeleton-text.carbon';
import {Skeleton} from './skeleton-text.shadcn';

const meta: Meta = {
  title: 'UI/SkeletonText',
};
export default meta;

type Story = StoryObj;

export const SingleLine: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (default 1 line)</div>
        <CarbonSkeletonText />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (single line, h-4)
        </div>
        <Skeleton className="h-4 w-full max-w-md" />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterSkeletonText />
      </div>
    </div>
  ),
};

export const Paragraph: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (<code>paragraph</code> + <code>lineCount=4</code>)
        </div>
        <CarbonSkeletonText paragraph lineCount={4} />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (stack 4 Skeletons; vary widths)
        </div>
        <div className="flex flex-col gap-2 max-w-md">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-11/12" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterSkeletonText paragraph lineCount={4} />
      </div>
    </div>
  ),
};

export const Heading: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (<code>heading</code>)
        </div>
        <CarbonSkeletonText heading />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (taller skeleton)
        </div>
        <Skeleton className="h-7 w-2/3 max-w-md" />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterSkeletonText heading />
      </div>
    </div>
  ),
};

export const Width: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (<code>width="200px"</code>)
        </div>
        <CarbonSkeletonText width="200px" />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (Tailwind width)</div>
        <Skeleton className="h-4 w-[200px]" />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterSkeletonText width="200px" />
      </div>
    </div>
  ),
};

export const TextAndIconRow: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (Skeleton family)
        </div>
        <div className="flex items-center gap-3">
          <div className="size-4 bg-muted animate-pulse rounded" />
          <CarbonSkeletonText width="180px" />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (compose Skeletons in a flex row)
        </div>
        <div className="flex items-center gap-3">
          <Skeleton className="size-4" />
          <Skeleton className="h-4 w-[180px]" />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <div className="flex items-center gap-3">
          <div className="size-4 bg-muted animate-pulse rounded" />
          <AdapterSkeletonText width="180px" />
        </div>
      </div>
    </div>
  ),
};
