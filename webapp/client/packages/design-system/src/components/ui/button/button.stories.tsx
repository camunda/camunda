/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Button as AdapterButton} from './button.adapter';
import {Button as CarbonButton} from './button.carbon';
import {Button as ShadcnButton} from './button.shadcn';

const meta: Meta = {
  title: 'UI/Button',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonButton>Click me</CarbonButton>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <ShadcnButton>Click me</ShadcnButton>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterButton>Click me</AdapterButton>
      </div>
    </div>
  ),
};

export const Variants: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (kind)</div>
        <div className="flex flex-wrap gap-2">
          <CarbonButton kind="primary">Primary</CarbonButton>
          <CarbonButton kind="secondary">Secondary</CarbonButton>
          <CarbonButton kind="tertiary">Tertiary</CarbonButton>
          <CarbonButton kind="ghost">Ghost</CarbonButton>
          <CarbonButton kind="danger">Danger</CarbonButton>
          <CarbonButton kind="danger--tertiary">Danger tertiary</CarbonButton>
          <CarbonButton kind="danger--ghost">Danger ghost</CarbonButton>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (variant)</div>
        <div className="flex flex-wrap gap-2">
          <ShadcnButton variant="default">Default</ShadcnButton>
          <ShadcnButton variant="secondary">Secondary</ShadcnButton>
          <ShadcnButton variant="outline">Outline</ShadcnButton>
          <ShadcnButton variant="ghost">Ghost</ShadcnButton>
          <ShadcnButton variant="destructive">Destructive</ShadcnButton>
          <ShadcnButton variant="link">Link</ShadcnButton>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <div className="flex flex-wrap gap-2">
          <AdapterButton kind="primary">Primary</AdapterButton>
          <AdapterButton kind="secondary">Secondary</AdapterButton>
          <AdapterButton kind="tertiary">Tertiary</AdapterButton>
          <AdapterButton kind="ghost">Ghost</AdapterButton>
          <AdapterButton kind="danger">Danger</AdapterButton>
          <AdapterButton kind="danger--tertiary">Danger tertiary</AdapterButton>
          <AdapterButton kind="danger--ghost">Danger ghost</AdapterButton>
        </div>
      </div>
    </div>
  ),
};

export const Sizes: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (size)</div>
        <div className="flex flex-wrap items-center gap-2">
          <CarbonButton size="sm">sm</CarbonButton>
          <CarbonButton size="md">md</CarbonButton>
          <CarbonButton size="lg">lg</CarbonButton>
          <CarbonButton size="xl">xl</CarbonButton>
          <CarbonButton size="2xl">2xl</CarbonButton>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (size)</div>
        <div className="flex flex-wrap items-center gap-2">
          <ShadcnButton size="sm">sm</ShadcnButton>
          <ShadcnButton size="default">default</ShadcnButton>
          <ShadcnButton size="lg">lg</ShadcnButton>
          <ShadcnButton size="icon" aria-label="icon">
            <span aria-hidden>★</span>
          </ShadcnButton>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <div className="flex flex-wrap items-center gap-2">
          <AdapterButton size="sm">sm</AdapterButton>
          <AdapterButton size="md">md</AdapterButton>
          <AdapterButton size="lg">lg</AdapterButton>
          <AdapterButton size="xl">xl</AdapterButton>
          <AdapterButton size="2xl">2xl</AdapterButton>
        </div>
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonButton disabled>Disabled</CarbonButton>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <ShadcnButton disabled>Disabled</ShadcnButton>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterButton disabled>Disabled</AdapterButton>
      </div>
    </div>
  ),
};
