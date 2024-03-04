/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
  scrollableContainerRef: React.RefObject<HTMLElement>;
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
