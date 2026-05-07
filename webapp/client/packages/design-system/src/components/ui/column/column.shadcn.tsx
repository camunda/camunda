/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';
import {useGrid} from '../grid/grid.shadcn';

type ColumnSpanObject = {
  span?: number | boolean;
  offset?: number;
  start?: number;
  end?: number;
};

type ColumnSpan = number | boolean | ColumnSpanObject;

type Breakpoint = 'sm' | 'md' | 'lg' | 'xl' | 'max';

type ColumnProps<E extends React.ElementType> = {
  as?: E;
  span?: ColumnSpan;
  sm?: ColumnSpan;
  md?: ColumnSpan;
  lg?: ColumnSpan;
  xl?: ColumnSpan;
  max?: ColumnSpan;
} & Omit<React.ComponentPropsWithoutRef<E>, 'as'>;

const BREAKPOINT_MIN_PX: Record<Breakpoint, number> = {
  sm: 0,
  md: 672,
  lg: 1056,
  xl: 1312,
  max: 1584,
};

function resolveSpan(value: ColumnSpan | undefined, totalCols: number) {
  if (value === undefined) return null;
  if (value === true) return {span: totalCols};
  if (value === false) return null;
  if (typeof value === 'number') return {span: value};
  return value;
}

function buildGridStyle(spans: Array<{
  bp: Breakpoint;
  resolved: ReturnType<typeof resolveSpan>;
}>) {
  // Smallest matching breakpoint wins; sort ascending by min-width.
  const sorted = [...spans].sort(
    (a, b) => BREAKPOINT_MIN_PX[a.bp] - BREAKPOINT_MIN_PX[b.bp],
  );
  return sorted;
}

function spanToCss(resolved: ColumnSpanObject) {
  if (resolved.start !== undefined && resolved.end !== undefined) {
    return `${resolved.start} / ${resolved.end}`;
  }
  const offset = resolved.offset ? `${resolved.offset + 1}` : 'auto';
  if (resolved.span !== undefined) {
    const span =
      typeof resolved.span === 'number'
        ? resolved.span
        : resolved.span === true
          ? -1
          : 1;
    return resolved.offset !== undefined
      ? `${offset} / span ${span}`
      : `span ${span}`;
  }
  return undefined;
}

function Column<E extends React.ElementType = 'div'>({
  as,
  span,
  sm,
  md,
  lg,
  xl,
  max,
  className,
  style,
  ...props
}: ColumnProps<E>) {
  const Comp = (as ?? 'div') as React.ElementType;
  const {columns} = useGrid();

  const constant = resolveSpan(span, columns);
  const responsive = buildGridStyle([
    {bp: 'sm', resolved: resolveSpan(sm, columns)},
    {bp: 'md', resolved: resolveSpan(md, columns)},
    {bp: 'lg', resolved: resolveSpan(lg, columns)},
    {bp: 'xl', resolved: resolveSpan(xl, columns)},
    {bp: 'max', resolved: resolveSpan(max, columns)},
  ]).filter((entry) => entry.resolved !== null);

  // Use container queries via inline custom properties — match Carbon's
  // breakpoint-driven column sizing with a small set of media queries
  // emitted as a `<style>` block scoped via a unique id.
  const id = React.useId().replace(/[^a-zA-Z0-9]/g, '');
  const slotClass = `cds-col-${id}`;

  const baseGridColumn = constant ? spanToCss(constant) : undefined;

  const css: string[] = [];
  if (responsive.length > 0) {
    for (const entry of responsive) {
      if (!entry.resolved) continue;
      const value = spanToCss(entry.resolved);
      if (!value) continue;
      const min = BREAKPOINT_MIN_PX[entry.bp];
      if (min === 0) {
        css.push(`.${slotClass}{grid-column:${value};}`);
      } else {
        css.push(`@media (min-width:${min}px){.${slotClass}{grid-column:${value};}}`);
      }
    }
  }

  return (
    <>
      {css.length > 0 && (
        // eslint-disable-next-line react/no-danger
        <style dangerouslySetInnerHTML={{__html: css.join('')}} />
      )}
      <Comp
        data-slot="column"
        className={cn(slotClass, className)}
        style={{
          gridColumn: baseGridColumn,
          ...style,
        }}
        {...props}
      />
    </>
  );
}

export {Column};
export type {ColumnProps, ColumnSpan, ColumnSpanObject};
