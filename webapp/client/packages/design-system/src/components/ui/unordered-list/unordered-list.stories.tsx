/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {
  UnorderedList as CarbonUnorderedList,
  ListItem as CarbonListItem,
} from '@carbon/react';
import {
  ListItem as AdapterListItem,
  UnorderedList as AdapterUnorderedList,
} from './unordered-list.adapter';
import {UnorderedList, ListItem} from './unordered-list.shadcn';

const meta: Meta = {
  title: 'UI/UnorderedList',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonUnorderedList>
          <CarbonListItem>Apples</CarbonListItem>
          <CarbonListItem>Oranges</CarbonListItem>
          <CarbonListItem>Bananas</CarbonListItem>
        </CarbonUnorderedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <UnorderedList>
          <ListItem>Apples</ListItem>
          <ListItem>Oranges</ListItem>
          <ListItem>Bananas</ListItem>
        </UnorderedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterUnorderedList>
          <AdapterListItem>Apples</AdapterListItem>
          <AdapterListItem>Oranges</AdapterListItem>
          <AdapterListItem>Bananas</AdapterListItem>
        </AdapterUnorderedList>
      </div>
    </div>
  ),
};

export const Nested: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonUnorderedList>
          <CarbonListItem>
            Fruits
            <CarbonUnorderedList nested>
              <CarbonListItem>Apples</CarbonListItem>
              <CarbonListItem>Oranges</CarbonListItem>
            </CarbonUnorderedList>
          </CarbonListItem>
          <CarbonListItem>Vegetables</CarbonListItem>
        </CarbonUnorderedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <UnorderedList>
          <ListItem>
            Fruits
            <UnorderedList nested>
              <ListItem>Apples</ListItem>
              <ListItem>Oranges</ListItem>
            </UnorderedList>
          </ListItem>
          <ListItem>Vegetables</ListItem>
        </UnorderedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterUnorderedList>
          <AdapterListItem>
            Fruits
            <AdapterUnorderedList nested>
              <AdapterListItem>Apples</AdapterListItem>
              <AdapterListItem>Oranges</AdapterListItem>
            </AdapterUnorderedList>
          </AdapterListItem>
          <AdapterListItem>Vegetables</AdapterListItem>
        </AdapterUnorderedList>
      </div>
    </div>
  ),
};

export const Expressive: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (isExpressive)</div>
        <CarbonUnorderedList isExpressive>
          <CarbonListItem>Larger type</CarbonListItem>
          <CarbonListItem>More breathing room</CarbonListItem>
        </CarbonUnorderedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (isExpressive)</div>
        <UnorderedList isExpressive>
          <ListItem>Larger type</ListItem>
          <ListItem>More breathing room</ListItem>
        </UnorderedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, isExpressive)
        </div>
        <AdapterUnorderedList isExpressive>
          <AdapterListItem>Larger type</AdapterListItem>
          <AdapterListItem>More breathing room</AdapterListItem>
        </AdapterUnorderedList>
      </div>
    </div>
  ),
};
