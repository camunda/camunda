/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {OrderedList as CarbonOrderedList} from './ordered-list.carbon';
import {OrderedList as AdapterOrderedList} from './ordered-list.adapter';
import {OrderedList} from './ordered-list.shadcn';

const meta: Meta = {title: 'UI/OrderedList'};
export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonOrderedList>
          <li>One step at a time</li>
          <li>Two steps at a time</li>
          <li>Three steps at a time</li>
        </CarbonOrderedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <OrderedList>
          <li>One step at a time</li>
          <li>Two steps at a time</li>
          <li>Three steps at a time</li>
        </OrderedList>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterOrderedList>
          <li>One step at a time</li>
          <li>Two steps at a time</li>
          <li>Three steps at a time</li>
        </AdapterOrderedList>
      </div>
    </div>
  ),
};

export const Nested: Story = {
  render: () => (
    <OrderedList>
      <li>
        Outer item one
        <OrderedList nested>
          <li>Inner item one</li>
          <li>Inner item two</li>
        </OrderedList>
      </li>
      <li>Outer item two</li>
    </OrderedList>
  ),
};

export const Expressive: Story = {
  render: () => (
    <OrderedList isExpressive>
      <li>Expressive larger text — first item</li>
      <li>Expressive larger text — second item</li>
    </OrderedList>
  ),
};
