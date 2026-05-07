/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {ChevronDown} from 'lucide-react';
import {
  MenuButton as AdapterMenuButton,
  MenuItem as AdapterMenuItem,
} from './menu-button.adapter';
import {
  MenuButton as CarbonMenuButton,
  MenuItem as CarbonMenuItem,
} from './menu-button.carbon';
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from './menu-button.shadcn';

const meta: Meta = {
  title: 'UI/MenuButton',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonMenuButton label="Actions">
          <CarbonMenuItem label="Edit" />
          <CarbonMenuItem label="Duplicate" />
          <CarbonMenuItem label="Archive" />
        </CarbonMenuButton>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (compose Button + DropdownMenu)
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline">
              Actions
              <ChevronDown className="ml-2 h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            <DropdownMenuItem>Edit</DropdownMenuItem>
            <DropdownMenuItem>Duplicate</DropdownMenuItem>
            <DropdownMenuItem>Archive</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterMenuButton label="Actions">
          <AdapterMenuItem label="Edit" />
          <AdapterMenuItem label="Duplicate" />
          <AdapterMenuItem label="Archive" />
        </AdapterMenuButton>
      </div>
    </div>
  ),
};

export const WithDestructive: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonMenuButton label="More">
          <CarbonMenuItem label="Edit" />
          <CarbonMenuItem label="Duplicate" />
          <CarbonMenuItem label="Delete" kind="danger" />
        </CarbonMenuButton>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline">
              More
              <ChevronDown className="ml-2 h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            <DropdownMenuItem>Edit</DropdownMenuItem>
            <DropdownMenuItem>Duplicate</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive">Delete</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterMenuButton label="More">
          <AdapterMenuItem label="Edit" />
          <AdapterMenuItem label="Duplicate" />
          <AdapterMenuItem label="Delete" kind="danger" />
        </AdapterMenuButton>
      </div>
    </div>
  ),
};

export const WithLabelAndGroups: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (no built-in label/group structure)
        </div>
        <CarbonMenuButton label="Account">
          <CarbonMenuItem label="Profile" />
          <CarbonMenuItem label="Settings" />
          <CarbonMenuItem label="Sign out" />
        </CarbonMenuButton>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (label + separators + groups)
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline">
              Account
              <ChevronDown className="ml-2 h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            <DropdownMenuLabel>My account</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem>Profile</DropdownMenuItem>
            <DropdownMenuItem>Settings</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem>Sign out</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterMenuButton label="Account">
          <AdapterMenuItem label="Profile" />
          <AdapterMenuItem label="Settings" />
          <AdapterMenuItem label="Sign out" />
        </AdapterMenuButton>
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonMenuButton label="Actions" disabled>
          <CarbonMenuItem label="Edit" />
          <CarbonMenuItem label="Duplicate" />
        </CarbonMenuButton>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" disabled>
              Actions
              <ChevronDown className="ml-2 h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
        </DropdownMenu>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterMenuButton label="Actions" disabled>
          <AdapterMenuItem label="Edit" />
          <AdapterMenuItem label="Duplicate" />
        </AdapterMenuButton>
      </div>
    </div>
  ),
};
