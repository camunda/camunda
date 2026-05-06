/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {SkeletonIcon as CarbonSkeletonIcon} from './skeleton-icon.carbon';
import {Skeleton} from './skeleton-icon.shadcn';

const meta: Meta = {
  title: 'UI/SkeletonIcon',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (16px)</div>
        <CarbonSkeletonIcon />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (sized via Tailwind)
        </div>
        <Skeleton className="size-4" />
      </div>
    </div>
  ),
};

export const Larger: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (24px)</div>
        <div className="[&_*]:!h-6 [&_*]:!w-6">
          <CarbonSkeletonIcon />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Skeleton className="size-6" />
      </div>
    </div>
  ),
};

export const InContext: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <div className="flex items-center gap-3">
          <CarbonSkeletonIcon />
          <span className="text-sm text-muted-foreground">
            Loading icon next to label…
          </span>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex items-center gap-3">
          <Skeleton className="size-4" />
          <span className="text-sm text-muted-foreground">
            Loading icon next to label…
          </span>
        </div>
      </div>
    </div>
  ),
};
