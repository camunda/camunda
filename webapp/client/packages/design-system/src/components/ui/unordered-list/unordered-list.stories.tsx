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
import {UnorderedList, ListItem} from './unordered-list.shadcn';

const meta: Meta = {
  title: 'UI/UnorderedList',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
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
    </div>
  ),
};

export const Nested: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
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
    </div>
  ),
};

export const Expressive: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
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
    </div>
  ),
};
