/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Plus, Settings, Trash2} from 'lucide-react';
import {IconButton as AdapterIconButton} from './icon-button.adapter';
import {IconButton as CarbonIconButton} from './icon-button.carbon';
import {
  Button,
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from './icon-button.shadcn';

const meta: Meta = {
  title: 'UI/IconButton',
};
export default meta;

type Story = StoryObj;

const ShadcnIconButton = ({
  label,
  icon: Icon,
  variant = 'ghost',
  disabled,
  destructive,
}: {
  label: string;
  icon: React.ComponentType<{className?: string}>;
  variant?: 'ghost' | 'outline' | 'default' | 'destructive';
  disabled?: boolean;
  destructive?: boolean;
}) => (
  <TooltipProvider delayDuration={100}>
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          variant={destructive ? 'destructive' : variant}
          size="icon"
          aria-label={label}
          disabled={disabled}
        >
          <Icon className="h-4 w-4" />
        </Button>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  </TooltipProvider>
);

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-12">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonIconButton label="Add item" align="bottom">
          <Plus />
        </CarbonIconButton>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (compose Button + Tooltip)
        </div>
        <ShadcnIconButton label="Add item" icon={Plus} />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterIconButton label="Add item" align="bottom">
          <Plus />
        </AdapterIconButton>
      </div>
    </div>
  ),
};

export const Variants: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-12">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (kind)</div>
        <div className="flex gap-2">
          <CarbonIconButton label="Primary" kind="primary">
            <Plus />
          </CarbonIconButton>
          <CarbonIconButton label="Secondary" kind="secondary">
            <Plus />
          </CarbonIconButton>
          <CarbonIconButton label="Tertiary" kind="tertiary">
            <Plus />
          </CarbonIconButton>
          <CarbonIconButton label="Ghost" kind="ghost">
            <Plus />
          </CarbonIconButton>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (variant)</div>
        <div className="flex gap-2">
          <ShadcnIconButton label="Default" icon={Plus} variant="default" />
          <ShadcnIconButton label="Outline" icon={Plus} variant="outline" />
          <ShadcnIconButton label="Ghost" icon={Plus} variant="ghost" />
          <ShadcnIconButton label="Destructive" icon={Trash2} destructive />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <div className="flex gap-2">
          <AdapterIconButton label="Primary" kind="primary">
            <Plus />
          </AdapterIconButton>
          <AdapterIconButton label="Secondary" kind="secondary">
            <Plus />
          </AdapterIconButton>
          <AdapterIconButton label="Tertiary" kind="tertiary">
            <Plus />
          </AdapterIconButton>
          <AdapterIconButton label="Ghost" kind="ghost">
            <Plus />
          </AdapterIconButton>
        </div>
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-12">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonIconButton label="Settings" disabled>
          <Settings />
        </CarbonIconButton>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <ShadcnIconButton label="Settings" icon={Settings} disabled />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterIconButton label="Settings" disabled>
          <Settings />
        </AdapterIconButton>
      </div>
    </div>
  ),
};
