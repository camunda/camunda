/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Link as CarbonLink} from '@carbon/react';
import {ArrowRight} from 'lucide-react';
import {Link as AdapterLink} from './link.adapter';
import {Link} from './link.shadcn';

const meta: Meta = {
  title: 'UI/Link',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonLink href="https://camunda.io">camunda.io</CarbonLink>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Link href="https://camunda.io">camunda.io</Link>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterLink href="https://camunda.io">camunda.io</AdapterLink>
      </div>
    </div>
  ),
};

export const Sizes: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <div className="flex flex-col gap-2">
          <CarbonLink size="sm" href="#">Small</CarbonLink>
          <CarbonLink size="md" href="#">Medium</CarbonLink>
          <CarbonLink size="lg" href="#">Large</CarbonLink>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-2">
          <Link size="sm" href="#">Small</Link>
          <Link size="md" href="#">Medium</Link>
          <Link size="lg" href="#">Large</Link>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <div className="flex flex-col gap-2">
          <AdapterLink size="sm" href="#">Small</AdapterLink>
          <AdapterLink size="md" href="#">Medium</AdapterLink>
          <AdapterLink size="lg" href="#">Large</AdapterLink>
        </div>
      </div>
    </div>
  ),
};

export const Inline: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (inline)</div>
        <p className="text-sm">
          Read the <CarbonLink inline href="#">full guide</CarbonLink> for details.
        </p>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (inline)</div>
        <p className="text-sm">
          Read the <Link inline href="#">full guide</Link> for details.
        </p>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, inline)
        </div>
        <p className="text-sm">
          Read the <AdapterLink inline href="#">full guide</AdapterLink> for
          details.
        </p>
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (disabled)</div>
        <CarbonLink disabled href="#">Unavailable</CarbonLink>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (disabled)</div>
        <Link disabled href="#">Unavailable</Link>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, disabled)
        </div>
        <AdapterLink disabled href="#">Unavailable</AdapterLink>
      </div>
    </div>
  ),
};

export const WithIcon: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (renderIcon)</div>
        <CarbonLink href="#" renderIcon={ArrowRight as never}>
          Continue
        </CarbonLink>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (renderIcon)</div>
        <Link href="#" renderIcon={ArrowRight}>
          Continue
        </Link>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, renderIcon)
        </div>
        <AdapterLink href="#" renderIcon={ArrowRight as never}>
          Continue
        </AdapterLink>
      </div>
    </div>
  ),
};
