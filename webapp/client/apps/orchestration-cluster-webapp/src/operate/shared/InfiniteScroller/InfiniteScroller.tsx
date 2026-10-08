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
	useLayoutEffect,
	useRef,
	useState,
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
	const [node, setNode] = useState<HTMLElement | null>(null);
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
				if (scrollTop === prevScrollTop) {
					// A DOM mutation (e.g. rows appended) can make the observer re-measure a
					// sentinel without any actual scrolling having happened, firing a callback
					// whose `isIntersecting` flips don't correspond to a real edge crossing.
					// Skip these: scrollTop is unchanged, so no direction can be determined, and
					// reacting to them (or even just updating `prevScrollTop`) risks swallowing
					// the next genuine crossing at that same position.
					return;
				}

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
			// rootMargin tolerates sub-pixel layout rounding: a zero-height sentinel can end up a
			// fraction of a pixel outside the root's bounds even when scrolled to the true edge,
			// which would otherwise permanently prevent it from ever being reported as intersecting.
			{root: scrollableContainerRef.current, threshold: 0.5, rootMargin: '1px'},
		);
	}, [scrollableContainerRef]);

	const observedContainerRef = useCallback((candidate: HTMLElement | null) => {
		setNode(candidate);
	}, []);

	// Runs as a layout effect (not inside the ref callback above) because React attaches refs
	// bottom-up: this component's ref can fire before the parent has attached
	// `scrollableContainerRef` to its own DOM node in the same commit. Constructing the
	// IntersectionObserver at that point would capture a stale `null` root and silently fall back
	// to the viewport. Layout effects only run once every ref in the commit has been attached, so
	// `scrollableContainerRef.current` is guaranteed to be set here.
	useLayoutEffect(() => {
		if (node === null) {
			return;
		}

		createIntersectionObserver();
		createMutationObserver(node);
		observeIntersections(node);

		return () => {
			intersectionObserver.current?.disconnect();
			mutationObserver.current?.disconnect();
		};
	}, [node, createIntersectionObserver, createMutationObserver]);

	if (isValidElement(children)) {
		return cloneElement(children, {ref: observedContainerRef});
	}

	console.error('No valid child element provided for InfiniteScroller');
	return null;
};

export {InfiniteScroller};
