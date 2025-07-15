/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useCallback, useRef} from 'react';
import {logger} from 'modules/logger';

type ChildProps = {
  ref: React.Ref<Element>;
};

type Props = {
  children: React.ReactElement<ChildProps>;
  onVerticalScrollStartReach?: (scrollUp: (distance: number) => void) => void;
  onVerticalScrollEndReach?: (scrollDown: (distance: number) => void) => void;
  scrollableContainerRef: React.RefObject<HTMLElement | null>;
};

const InfiniteScroller: React.FC<Props> = ({
  children,
  onVerticalScrollStartReach,
  onVerticalScrollEndReach,
  scrollableContainerRef,
}) => {
  const intersectionObserver = useRef<IntersectionObserver | null>(null);
  const mutationObserver = useRef<MutationObserver | null>(null);

  const observeIntersections = (node: HTMLElement) => {
    const firstChild = node.firstElementChild;
    const lastChild = node.lastElementChild;
    intersectionObserver.current?.disconnect();

    if (firstChild) {
      intersectionObserver.current?.observe(firstChild);
    }

    if (lastChild) {
      intersectionObserver.current?.observe(lastChild);
    }
  };

  const createMutationObserver = useCallback((node: HTMLElement) => {
    mutationObserver.current = new MutationObserver(() => {
      // re-observe the node after the list of children changes
      intersectionObserver.current?.disconnect();
      observeIntersections(node);
    });

    mutationObserver.current.observe(node, {childList: true});
  }, []);

  const createIntersectionObserver = useCallback(() => {
    const scrollDown = (distance: number) => {
      scrollableContainerRef.current?.scrollTo(
        0,
        scrollableContainerRef.current.scrollTop + distance,
      );
    };

    const scrollUp = (distance: number) => {
      scrollableContainerRef.current?.scrollTo(
        0,
        scrollableContainerRef.current.scrollTop - distance,
      );
    };

    let prevScrollTop = 0;
    intersectionObserver.current = new IntersectionObserver(
      (entries) => {
        if (scrollableContainerRef.current === null) {
          return;
        }

        const scrollTop = scrollableContainerRef.current.scrollTop || 0;
        entries
          .filter((entry) => entry.isIntersecting)
          .forEach(({target}) => {
            if (
              scrollTop > prevScrollTop &&
              target.parentElement?.lastElementChild === target
            ) {
              onVerticalScrollEndReach?.(scrollUp);
            } else if (
              scrollTop < prevScrollTop &&
              target.parentElement?.firstElementChild === target
            ) {
              onVerticalScrollStartReach?.(scrollDown);
            }
          });
        prevScrollTop = scrollTop;
      },
      {root: scrollableContainerRef.current, threshold: 0.5},
    );
  }, [
    onVerticalScrollStartReach,
    onVerticalScrollEndReach,
    scrollableContainerRef,
  ]);

  const observedContainerRef = useCallback(
    (node: HTMLElement) => {
      if (node === null) {
        return;
      }
      if (intersectionObserver.current === null) {
        createIntersectionObserver();
        createMutationObserver(node);
        observeIntersections(node);
      }
    },
    [createIntersectionObserver, createMutationObserver],
  );

  if (React.isValidElement(children)) {
    return React.cloneElement(children, {
      ref: observedContainerRef,
    });
  }

  logger.error('No valid child element provided for InfiniteScroller');
  return null;
};

export {InfiniteScroller};
