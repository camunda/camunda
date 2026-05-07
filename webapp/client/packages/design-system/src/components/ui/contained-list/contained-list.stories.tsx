/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {
  ContainedList as CarbonContainedList,
  ContainedListItem as CarbonContainedListItem,
} from '@carbon/react';
import {Star, Trash2} from 'lucide-react';
import {Button} from '../button/button.shadcn';
import {ContainedList, ContainedListItem} from './contained-list.shadcn';

const meta: Meta = {
  title: 'UI/ContainedList',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonContainedList label="Recent searches">
          <CarbonContainedListItem>Pizza</CarbonContainedListItem>
          <CarbonContainedListItem>Coffee</CarbonContainedListItem>
          <CarbonContainedListItem>Tacos</CarbonContainedListItem>
        </CarbonContainedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <ContainedList label="Recent searches">
          <ContainedListItem>Pizza</ContainedListItem>
          <ContainedListItem>Coffee</ContainedListItem>
          <ContainedListItem>Tacos</ContainedListItem>
        </ContainedList>
      </div>
    </div>
  ),
};

export const Disclosed: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (kind=disclosed)</div>
        <CarbonContainedList kind="disclosed" label="Saved filters">
          <CarbonContainedListItem>All instances</CarbonContainedListItem>
          <CarbonContainedListItem>Active</CarbonContainedListItem>
          <CarbonContainedListItem>Failed</CarbonContainedListItem>
        </CarbonContainedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (kind=disclosed)</div>
        <ContainedList kind="disclosed" label="Saved filters">
          <ContainedListItem>All instances</ContainedListItem>
          <ContainedListItem>Active</ContainedListItem>
          <ContainedListItem>Failed</ContainedListItem>
        </ContainedList>
      </div>
    </div>
  ),
};

export const WithActionAndIcons: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonContainedList
          label="Favorites"
          action={<button type="button">Edit</button>}
        >
          <CarbonContainedListItem
            renderIcon={Star as never}
            action={<button type="button">Remove</button>}
          >
            Item one
          </CarbonContainedListItem>
          <CarbonContainedListItem renderIcon={Star as never}>
            Item two
          </CarbonContainedListItem>
        </CarbonContainedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <ContainedList
          label="Favorites"
          action={<Button size="sm" variant="ghost">Edit</Button>}
        >
          <ContainedListItem
            renderIcon={Star}
            action={
              <Button size="icon" variant="ghost" aria-label="Remove">
                <Trash2 className="size-4" />
              </Button>
            }
          >
            Item one
          </ContainedListItem>
          <ContainedListItem renderIcon={Star}>Item two</ContainedListItem>
        </ContainedList>
      </div>
    </div>
  ),
};

export const Interactive: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (onClick)</div>
        <CarbonContainedList label="Pages">
          <CarbonContainedListItem onClick={() => alert('one')}>
            Page one
          </CarbonContainedListItem>
          <CarbonContainedListItem onClick={() => alert('two')}>
            Page two
          </CarbonContainedListItem>
          <CarbonContainedListItem disabled>Page three (disabled)</CarbonContainedListItem>
        </CarbonContainedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (onClick)</div>
        <ContainedList label="Pages">
          <ContainedListItem onClick={() => alert('one')}>
            Page one
          </ContainedListItem>
          <ContainedListItem onClick={() => alert('two')}>
            Page two
          </ContainedListItem>
          <ContainedListItem disabled>Page three (disabled)</ContainedListItem>
        </ContainedList>
      </div>
    </div>
  ),
};

export const Sizes: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <div className="flex flex-col gap-4">
          {(['sm', 'md', 'lg', 'xl'] as const).map((size) => (
            <CarbonContainedList key={size} label={size} size={size}>
              <CarbonContainedListItem>{size} item</CarbonContainedListItem>
            </CarbonContainedList>
          ))}
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-4">
          {(['sm', 'md', 'lg', 'xl'] as const).map((size) => (
            <ContainedList key={size} label={size} size={size}>
              <ContainedListItem>{size} item</ContainedListItem>
            </ContainedList>
          ))}
        </div>
      </div>
    </div>
  ),
};
