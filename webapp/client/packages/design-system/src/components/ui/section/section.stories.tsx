/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Section as CarbonSection, Heading as CarbonHeading} from '@carbon/react';
import {Heading as AdapterHeading} from '../heading/heading.adapter';
import {Heading} from '../heading/heading.shadcn';
import {Section as AdapterSection} from './section.adapter';
import {Section} from './section.shadcn';

const meta: Meta = {
  title: 'UI/Section',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonSection>
          <CarbonHeading>Top-level (h1)</CarbonHeading>
          <CarbonSection>
            <CarbonHeading>Nested (h2)</CarbonHeading>
            <CarbonSection>
              <CarbonHeading>Deeper (h3)</CarbonHeading>
            </CarbonSection>
          </CarbonSection>
        </CarbonSection>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Section>
          <Heading>Top-level (h1)</Heading>
          <Section>
            <Heading>Nested (h2)</Heading>
            <Section>
              <Heading>Deeper (h3)</Heading>
            </Section>
          </Section>
        </Section>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterSection>
          <AdapterHeading>Top-level (h1)</AdapterHeading>
          <AdapterSection>
            <AdapterHeading>Nested (h2)</AdapterHeading>
            <AdapterSection>
              <AdapterHeading>Deeper (h3)</AdapterHeading>
            </AdapterSection>
          </AdapterSection>
        </AdapterSection>
      </div>
    </div>
  ),
};

export const ExplicitLevel: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (level=3)</div>
        <CarbonSection level={3}>
          <CarbonHeading>Forced h3</CarbonHeading>
        </CarbonSection>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (level=3)</div>
        <Section level={3}>
          <Heading>Forced h3</Heading>
        </Section>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, level=3)
        </div>
        <AdapterSection level={3}>
          <AdapterHeading>Forced h3</AdapterHeading>
        </AdapterSection>
      </div>
    </div>
  ),
};

export const PolymorphicAs: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (as=&quot;article&quot;)</div>
        <CarbonSection as="article">
          <CarbonHeading>Article heading</CarbonHeading>
          <p className="text-sm text-muted-foreground">
            Renders an &lt;article&gt; element instead of &lt;section&gt;.
          </p>
        </CarbonSection>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (as=&quot;article&quot;)</div>
        <Section as="article">
          <Heading>Article heading</Heading>
          <p className="text-sm text-muted-foreground">
            Renders an &lt;article&gt; element instead of &lt;section&gt;.
          </p>
        </Section>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, as=&quot;article&quot;)
        </div>
        <AdapterSection as="article">
          <AdapterHeading>Article heading</AdapterHeading>
          <p className="text-sm text-muted-foreground">
            Renders an &lt;article&gt; element instead of &lt;section&gt;.
          </p>
        </AdapterSection>
      </div>
    </div>
  ),
};
