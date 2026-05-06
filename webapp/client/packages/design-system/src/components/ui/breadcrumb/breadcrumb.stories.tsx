/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {
  Breadcrumb as CarbonBreadcrumb,
  BreadcrumbItem as CarbonBreadcrumbItem,
} from './breadcrumb.carbon';
import {
  Breadcrumb as ShadcnBreadcrumb,
  BreadcrumbEllipsis,
  BreadcrumbItem as ShadcnBreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from './breadcrumb.shadcn';

const meta: Meta = {
  title: 'UI/Breadcrumb',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonBreadcrumb>
          <CarbonBreadcrumbItem href="#">Home</CarbonBreadcrumbItem>
          <CarbonBreadcrumbItem href="#">Components</CarbonBreadcrumbItem>
          <CarbonBreadcrumbItem isCurrentPage>Breadcrumb</CarbonBreadcrumbItem>
        </CarbonBreadcrumb>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <ShadcnBreadcrumb>
          <BreadcrumbList>
            <ShadcnBreadcrumbItem>
              <BreadcrumbLink href="#">Home</BreadcrumbLink>
            </ShadcnBreadcrumbItem>
            <BreadcrumbSeparator />
            <ShadcnBreadcrumbItem>
              <BreadcrumbLink href="#">Components</BreadcrumbLink>
            </ShadcnBreadcrumbItem>
            <BreadcrumbSeparator />
            <ShadcnBreadcrumbItem>
              <BreadcrumbPage>Breadcrumb</BreadcrumbPage>
            </ShadcnBreadcrumbItem>
          </BreadcrumbList>
        </ShadcnBreadcrumb>
      </div>
    </div>
  ),
};

export const WithEllipsis: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonBreadcrumb>
          <CarbonBreadcrumbItem href="#">Home</CarbonBreadcrumbItem>
          <CarbonBreadcrumbItem href="#">…</CarbonBreadcrumbItem>
          <CarbonBreadcrumbItem href="#">Components</CarbonBreadcrumbItem>
          <CarbonBreadcrumbItem isCurrentPage>Breadcrumb</CarbonBreadcrumbItem>
        </CarbonBreadcrumb>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <ShadcnBreadcrumb>
          <BreadcrumbList>
            <ShadcnBreadcrumbItem>
              <BreadcrumbLink href="#">Home</BreadcrumbLink>
            </ShadcnBreadcrumbItem>
            <BreadcrumbSeparator />
            <ShadcnBreadcrumbItem>
              <BreadcrumbEllipsis />
            </ShadcnBreadcrumbItem>
            <BreadcrumbSeparator />
            <ShadcnBreadcrumbItem>
              <BreadcrumbLink href="#">Components</BreadcrumbLink>
            </ShadcnBreadcrumbItem>
            <BreadcrumbSeparator />
            <ShadcnBreadcrumbItem>
              <BreadcrumbPage>Breadcrumb</BreadcrumbPage>
            </ShadcnBreadcrumbItem>
          </BreadcrumbList>
        </ShadcnBreadcrumb>
      </div>
    </div>
  ),
};
