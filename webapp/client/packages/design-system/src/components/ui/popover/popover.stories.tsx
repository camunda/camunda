/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {
  Popover as CarbonPopover,
  PopoverContent as CarbonPopoverContent,
} from './popover.carbon';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from './popover.shadcn';

const meta: Meta = {
  title: 'UI/Popover',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-12">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonPopover open align="bottom-start">
          <button type="button" className="underline">
            Trigger
          </button>
          <CarbonPopoverContent>
            <div className="p-4 max-w-xs">
              <h4 className="font-medium mb-2">Carbon popover</h4>
              <p className="text-sm">
                Anchored to the trigger; controlled via the <code>open</code>{' '}
                prop.
              </p>
            </div>
          </CarbonPopoverContent>
        </CarbonPopover>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Popover defaultOpen>
          <PopoverTrigger asChild>
            <button type="button" className="underline">
              Trigger
            </button>
          </PopoverTrigger>
          <PopoverContent>
            <h4 className="font-medium mb-2">shadcn popover</h4>
            <p className="text-sm">
              Anchored to the trigger; uncontrolled via{' '}
              <code>defaultOpen</code>.
            </p>
          </PopoverContent>
        </Popover>
      </div>
    </div>
  ),
};

export const Sides: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-32">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <div className="flex gap-12">
          <CarbonPopover open align="top">
            <button type="button" className="underline">
              top
            </button>
            <CarbonPopoverContent>
              <div className="p-2 text-xs">top</div>
            </CarbonPopoverContent>
          </CarbonPopover>
          <CarbonPopover open align="right">
            <button type="button" className="underline">
              right
            </button>
            <CarbonPopoverContent>
              <div className="p-2 text-xs">right</div>
            </CarbonPopoverContent>
          </CarbonPopover>
          <CarbonPopover open align="bottom">
            <button type="button" className="underline">
              bottom
            </button>
            <CarbonPopoverContent>
              <div className="p-2 text-xs">bottom</div>
            </CarbonPopoverContent>
          </CarbonPopover>
          <CarbonPopover open align="left">
            <button type="button" className="underline">
              left
            </button>
            <CarbonPopoverContent>
              <div className="p-2 text-xs">left</div>
            </CarbonPopoverContent>
          </CarbonPopover>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex gap-12">
          <Popover defaultOpen>
            <PopoverTrigger asChild>
              <button type="button" className="underline">
                top
              </button>
            </PopoverTrigger>
            <PopoverContent side="top" className="w-auto p-2 text-xs">
              top
            </PopoverContent>
          </Popover>
          <Popover defaultOpen>
            <PopoverTrigger asChild>
              <button type="button" className="underline">
                right
              </button>
            </PopoverTrigger>
            <PopoverContent side="right" className="w-auto p-2 text-xs">
              right
            </PopoverContent>
          </Popover>
          <Popover defaultOpen>
            <PopoverTrigger asChild>
              <button type="button" className="underline">
                bottom
              </button>
            </PopoverTrigger>
            <PopoverContent side="bottom" className="w-auto p-2 text-xs">
              bottom
            </PopoverContent>
          </Popover>
          <Popover defaultOpen>
            <PopoverTrigger asChild>
              <button type="button" className="underline">
                left
              </button>
            </PopoverTrigger>
            <PopoverContent side="left" className="w-auto p-2 text-xs">
              left
            </PopoverContent>
          </Popover>
        </div>
      </div>
    </div>
  ),
};
