/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {logger} from 'modules/logger';

type Props = {
  children?: React.ReactNode;
  onVerticalScrollStartReach?: (scrollUp: (distance: number) => void) => void;
  onVerticalScrollEndReach?: (scrollDown: (distance: number) => void) => void;
};

const InfiniteScroller: React.FC<Props> = ({
  children,
  onVerticalScrollStartReach,
  onVerticalScrollEndReach,
}) => {
  const handleScroll = (event: React.UIEvent<HTMLElement, UIEvent>) => {
    const target = event.target as HTMLElement;

    const scrollUp = (distance: number) => {
      target.scrollTo(0, target.scrollTop - distance);
    };

    const scrollDown = (distance: number) => {
      target.scrollTo(0, target.scrollTop + distance);
    };

    if (target.scrollHeight - target.clientHeight - target.scrollTop <= 0) {
      onVerticalScrollEndReach?.(scrollUp);
    }

    if (target.scrollTop === 0) {
      onVerticalScrollStartReach?.(scrollDown);
    }
  };

  if (React.isValidElement(children)) {
    return React.cloneElement(children, {
      onScroll: handleScroll,
    });
  }

  logger.error('No valid child element provided for InfiniteScroller');
  return null;
};

export {InfiniteScroller};
