/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `Tile` is a generic content container; shadcn's closest
 * primitive is `Card`. Carbon's experimental decorator/AILabel slot,
 * deprecated `slug`/`light`, and `hasRoundedCorners` (only meaningful
 * with an AILabel present) have no shadcn analogue and are dropped.
 */

import * as React from 'react';

import {Card} from '../card/card.shadcn';

import {cn, warnDroppedProps} from '../../../lib/utils';

import type {TileProps as CarbonTileProps} from '@carbon/react';

export type TileProps = CarbonTileProps;

function Tile(props: TileProps) {
  const {
    children,
    className,
    decorator,
    hasRoundedCorners,
    light,
    slug,
    ...rest
  } = props;

  warnDroppedProps('Tile', {
    decorator,
    hasRoundedCorners,
    light,
    slug,
  });

  return (
    <Card
      className={cn('px-6', className)}
      {...(rest as React.ComponentProps<typeof Card>)}
    >
      {children}
    </Card>
  );
}

export {Tile};
