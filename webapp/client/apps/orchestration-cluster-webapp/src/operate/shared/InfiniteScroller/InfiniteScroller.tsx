/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	cloneElement,
	isValidElement,
	useCallback,
	useRef,
	type FC,
	type ReactElement,
	type Ref,
	type RefObject,
} from 'react';

type ChildProps = {
	ref: Ref<Element>;
};

type Props = {
	children: ReactElement<ChildProps>;
	onVerticalScrollStartReach?: (scrollUp: (distance: number) => void) => void;
	onVerticalScrollEndReach?: (scrollDown: (distance: number) => void) => void;
	scrollableContainerRef: RefObject<HTMLElement | null>;
};

const InfiniteScroller: FC<Props> = ({
	children,
	onVerticalScrollStartReach,
	onVerticalScrollEndReach,
	scrollableContainerRef,
}) => {
	const intersectionObserver = useRef<IntersectionObserver | null>(null);
	const mutationObserver = useRef<MutationObserver | null>(null);

	// Box callbacks in refs so the observer always uses the latest version.
	const onVerticalScrollStartReachRef = useRef(onVerticalScrollStartReach);
	const onVerticalScrollEndReachRef = useRef(onVerticalScrollEndReach);
	// eslint-disable-next-line react-hooks/refs
	onVerticalScrollStartReachRef.current = onVerticalScrollStartReach;
	// eslint-disable-next-line react-hooks/refs
	onVerticalScrollEndReachRef.current = onVerticalScrollEndReach;

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
			intersectionObserver.current?.disconnect();
			observeIntersections(node);
		});

		mutationObserver.current.observe(node, {childList: true});
	}, []);

	const createIntersectionObserver = useCallback(() => {
		const scrollDown = (distance: number) => {
			scrollableContainerRef.current?.scrollTo(0, scrollableContainerRef.current.scrollTop + distance);
		};

		const scrollUp = (distance: number) => {
			scrollableContainerRef.current?.scrollTo(0, scrollableContainerRef.current.scrollTop - distance);
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
						if (scrollTop > prevScrollTop && target.parentElement?.lastElementChild === target) {
							onVerticalScrollEndReachRef.current?.(scrollUp);
						} else if (scrollTop < prevScrollTop && target.parentElement?.firstElementChild === target) {
							onVerticalScrollStartReachRef.current?.(scrollDown);
						}
					});
				prevScrollTop = scrollTop;
			},
			{root: scrollableContainerRef.current, threshold: 0.5},
		);
	}, [scrollableContainerRef]);

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

	if (isValidElement(children)) {
		return cloneElement(children, {
			ref: observedContainerRef,
		});
	}

	console.error('No valid child element provided for InfiniteScroller');
	return null;
};

export {InfiniteScroller};
