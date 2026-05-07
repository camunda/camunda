/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Tooltip as AdapterTooltip} from './tooltip.adapter';
import {Tooltip as CarbonTooltip} from './tooltip.carbon';
import {
  Tooltip as ShadcnTooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from './tooltip.shadcn';

const meta: Meta = {
  title: 'UI/Tooltip',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-16">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTooltip label="This is a Carbon tooltip" defaultOpen>
          <button type="button" className="underline">
            Hover me
          </button>
        </CarbonTooltip>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <TooltipProvider>
          <ShadcnTooltip defaultOpen>
            <TooltipTrigger asChild>
              <button type="button" className="underline">
                Hover me
              </button>
            </TooltipTrigger>
            <TooltipContent>This is a shadcn tooltip</TooltipContent>
          </ShadcnTooltip>
        </TooltipProvider>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTooltip label="This is an Adapter tooltip" defaultOpen>
          <button type="button" className="underline">
            Hover me
          </button>
        </AdapterTooltip>
      </div>
    </div>
  ),
};

export const Sides: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-32">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <div className="flex gap-12">
          <CarbonTooltip label="top" align="top" defaultOpen>
            <button type="button" className="underline">
              top
            </button>
          </CarbonTooltip>
          <CarbonTooltip label="right" align="right" defaultOpen>
            <button type="button" className="underline">
              right
            </button>
          </CarbonTooltip>
          <CarbonTooltip label="bottom" align="bottom" defaultOpen>
            <button type="button" className="underline">
              bottom
            </button>
          </CarbonTooltip>
          <CarbonTooltip label="left" align="left" defaultOpen>
            <button type="button" className="underline">
              left
            </button>
          </CarbonTooltip>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <TooltipProvider>
          <div className="flex gap-12">
            <ShadcnTooltip defaultOpen>
              <TooltipTrigger asChild>
                <button type="button" className="underline">
                  top
                </button>
              </TooltipTrigger>
              <TooltipContent side="top">top</TooltipContent>
            </ShadcnTooltip>
            <ShadcnTooltip defaultOpen>
              <TooltipTrigger asChild>
                <button type="button" className="underline">
                  right
                </button>
              </TooltipTrigger>
              <TooltipContent side="right">right</TooltipContent>
            </ShadcnTooltip>
            <ShadcnTooltip defaultOpen>
              <TooltipTrigger asChild>
                <button type="button" className="underline">
                  bottom
                </button>
              </TooltipTrigger>
              <TooltipContent side="bottom">bottom</TooltipContent>
            </ShadcnTooltip>
            <ShadcnTooltip defaultOpen>
              <TooltipTrigger asChild>
                <button type="button" className="underline">
                  left
                </button>
              </TooltipTrigger>
              <TooltipContent side="left">left</TooltipContent>
            </ShadcnTooltip>
          </div>
        </TooltipProvider>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <div className="flex gap-12">
          <AdapterTooltip label="top" align="top" defaultOpen>
            <button type="button" className="underline">
              top
            </button>
          </AdapterTooltip>
          <AdapterTooltip label="right" align="right" defaultOpen>
            <button type="button" className="underline">
              right
            </button>
          </AdapterTooltip>
          <AdapterTooltip label="bottom" align="bottom" defaultOpen>
            <button type="button" className="underline">
              bottom
            </button>
          </AdapterTooltip>
          <AdapterTooltip label="left" align="left" defaultOpen>
            <button type="button" className="underline">
              left
            </button>
          </AdapterTooltip>
        </div>
      </div>
    </div>
  ),
};

export const RichContent: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-16">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTooltip
          label={
            <div>
              <div className="font-medium">Heads up</div>
              <div className="text-xs">
                Carbon tooltips accept ReactNode in <code>label</code>.
              </div>
            </div>
          }
          defaultOpen
        >
          <button type="button" className="underline">
            Hover for details
          </button>
        </CarbonTooltip>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <TooltipProvider>
          <ShadcnTooltip defaultOpen>
            <TooltipTrigger asChild>
              <button type="button" className="underline">
                Hover for details
              </button>
            </TooltipTrigger>
            <TooltipContent>
              <div className="font-medium">Heads up</div>
              <div className="text-xs">
                shadcn TooltipContent accepts arbitrary children.
              </div>
            </TooltipContent>
          </ShadcnTooltip>
        </TooltipProvider>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTooltip
          label={
            <div>
              <div className="font-medium">Heads up</div>
              <div className="text-xs">
                Carbon tooltips accept ReactNode in <code>label</code>.
              </div>
            </div>
          }
          defaultOpen
        >
          <button type="button" className="underline">
            Hover for details
          </button>
        </AdapterTooltip>
      </div>
    </div>
  ),
};
