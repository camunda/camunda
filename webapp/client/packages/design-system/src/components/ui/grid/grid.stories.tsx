/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Grid as CarbonGrid, Column as CarbonColumn} from '@carbon/react';
import {Column as AdapterColumn} from '../column/column.adapter';
import {Column} from '../column/column.shadcn';
import {Grid as AdapterGrid} from './grid.adapter';
import {Grid} from './grid.shadcn';

const meta: Meta = {
  title: 'UI/Grid',
};
export default meta;

type Story = StoryObj;

const Box = ({children}: {children: React.ReactNode}) => (
  <div className="rounded-md border bg-muted px-3 py-2 text-sm">{children}</div>
);

export const Default: Story = {
  render: () => (
    <div className="flex flex-col gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonGrid>
          <CarbonColumn span={4}>
            <Box>span=4</Box>
          </CarbonColumn>
          <CarbonColumn span={8}>
            <Box>span=8</Box>
          </CarbonColumn>
          <CarbonColumn span={4}>
            <Box>span=4</Box>
          </CarbonColumn>
        </CarbonGrid>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Grid>
          <Column span={4}>
            <Box>span=4</Box>
          </Column>
          <Column span={8}>
            <Box>span=8</Box>
          </Column>
          <Column span={4}>
            <Box>span=4</Box>
          </Column>
        </Grid>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterGrid>
          <AdapterColumn span={4}>
            <Box>span=4</Box>
          </AdapterColumn>
          <AdapterColumn span={8}>
            <Box>span=8</Box>
          </AdapterColumn>
          <AdapterColumn span={4}>
            <Box>span=4</Box>
          </AdapterColumn>
        </AdapterGrid>
      </div>
    </div>
  ),
};

export const Responsive: Story = {
  render: () => (
    <div className="flex flex-col gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonGrid>
          <CarbonColumn sm={4} md={4} lg={8}>
            <Box>sm=4 md=4 lg=8</Box>
          </CarbonColumn>
          <CarbonColumn sm={4} md={4} lg={8}>
            <Box>sm=4 md=4 lg=8</Box>
          </CarbonColumn>
        </CarbonGrid>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Grid>
          <Column sm={4} md={4} lg={8}>
            <Box>sm=4 md=4 lg=8</Box>
          </Column>
          <Column sm={4} md={4} lg={8}>
            <Box>sm=4 md=4 lg=8</Box>
          </Column>
        </Grid>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterGrid>
          <AdapterColumn sm={4} md={4} lg={8}>
            <Box>sm=4 md=4 lg=8</Box>
          </AdapterColumn>
          <AdapterColumn sm={4} md={4} lg={8}>
            <Box>sm=4 md=4 lg=8</Box>
          </AdapterColumn>
        </AdapterGrid>
      </div>
    </div>
  ),
};

export const Condensed: Story = {
  render: () => (
    <div className="flex flex-col gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (condensed)</div>
        <CarbonGrid condensed>
          <CarbonColumn span={8}>
            <Box>One</Box>
          </CarbonColumn>
          <CarbonColumn span={8}>
            <Box>Two</Box>
          </CarbonColumn>
        </CarbonGrid>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (condensed)</div>
        <Grid condensed>
          <Column span={8}>
            <Box>One</Box>
          </Column>
          <Column span={8}>
            <Box>Two</Box>
          </Column>
        </Grid>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, condensed)
        </div>
        <AdapterGrid condensed>
          <AdapterColumn span={8}>
            <Box>One</Box>
          </AdapterColumn>
          <AdapterColumn span={8}>
            <Box>Two</Box>
          </AdapterColumn>
        </AdapterGrid>
      </div>
    </div>
  ),
};
