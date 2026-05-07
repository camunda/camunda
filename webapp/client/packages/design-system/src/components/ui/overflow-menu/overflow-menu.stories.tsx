/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {MoreVertical} from 'lucide-react';
import {
  OverflowMenu as AdapterOverflowMenu,
  OverflowMenuItem as AdapterOverflowMenuItem,
} from './overflow-menu.adapter';
import {
  OverflowMenu as CarbonOverflowMenu,
  OverflowMenuItem as CarbonOverflowMenuItem,
} from './overflow-menu.carbon';
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from './overflow-menu.shadcn';

const meta: Meta = {
  title: 'UI/OverflowMenu',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonOverflowMenu aria-label="Row actions">
          <CarbonOverflowMenuItem itemText="Edit" />
          <CarbonOverflowMenuItem itemText="Duplicate" />
          <CarbonOverflowMenuItem itemText="Archive" />
        </CarbonOverflowMenu>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (compose icon Button + DropdownMenu)
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" aria-label="Row actions">
              <MoreVertical className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem>Edit</DropdownMenuItem>
            <DropdownMenuItem>Duplicate</DropdownMenuItem>
            <DropdownMenuItem>Archive</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterOverflowMenu aria-label="Row actions">
          <AdapterOverflowMenuItem itemText="Edit" />
          <AdapterOverflowMenuItem itemText="Duplicate" />
          <AdapterOverflowMenuItem itemText="Archive" />
        </AdapterOverflowMenu>
      </div>
    </div>
  ),
};

export const WithDangerItem: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonOverflowMenu aria-label="Row actions">
          <CarbonOverflowMenuItem itemText="Edit" />
          <CarbonOverflowMenuItem itemText="Duplicate" />
          <CarbonOverflowMenuItem itemText="Delete" isDelete hasDivider />
        </CarbonOverflowMenu>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" aria-label="Row actions">
              <MoreVertical className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem>Edit</DropdownMenuItem>
            <DropdownMenuItem>Duplicate</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive">Delete</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterOverflowMenu aria-label="Row actions">
          <AdapterOverflowMenuItem itemText="Edit" />
          <AdapterOverflowMenuItem itemText="Duplicate" />
          <AdapterOverflowMenuItem itemText="Delete" isDelete hasDivider />
        </AdapterOverflowMenu>
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonOverflowMenu aria-label="Row actions" disabled>
          <CarbonOverflowMenuItem itemText="Edit" />
          <CarbonOverflowMenuItem itemText="Duplicate" />
        </CarbonOverflowMenu>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Row actions"
              disabled
            >
              <MoreVertical className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
        </DropdownMenu>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterOverflowMenu aria-label="Row actions" disabled>
          <AdapterOverflowMenuItem itemText="Edit" />
          <AdapterOverflowMenuItem itemText="Duplicate" />
        </AdapterOverflowMenu>
      </div>
    </div>
  ),
};

export const Flipped: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (<code>flipped</code> + <code>direction="top"</code>)
        </div>
        <div className="flex justify-end pt-32">
          <CarbonOverflowMenu
            aria-label="Row actions"
            flipped
            direction="top"
          >
            <CarbonOverflowMenuItem itemText="Edit" />
            <CarbonOverflowMenuItem itemText="Duplicate" />
          </CarbonOverflowMenu>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (<code>side="top" align="end"</code>)
        </div>
        <div className="flex justify-end pt-32">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" aria-label="Row actions">
                <MoreVertical className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent side="top" align="end">
              <DropdownMenuItem>Edit</DropdownMenuItem>
              <DropdownMenuItem>Duplicate</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, flipped + direction=top)
        </div>
        <div className="flex justify-end pt-32">
          <AdapterOverflowMenu
            aria-label="Row actions"
            flipped
            direction="top"
          >
            <AdapterOverflowMenuItem itemText="Edit" />
            <AdapterOverflowMenuItem itemText="Duplicate" />
          </AdapterOverflowMenu>
        </div>
      </div>
    </div>
  ),
};
