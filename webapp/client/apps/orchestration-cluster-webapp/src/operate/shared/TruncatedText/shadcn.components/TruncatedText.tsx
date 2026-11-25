/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useState} from 'react';
import {Tooltip, TooltipContent, TooltipTrigger, useIsTruncated} from '@camunda/design-system';
import {cn} from '#/shared/cn';

const FOCUSABLE_ANCESTOR_SELECTOR = 'a[href], button, [role="treeitem"]';

type Props = {
	text: string;
	children?: React.ReactNode;
	className?: string;
};

const TruncatedText: React.FC<Props> = ({text, children = text, className}) => {
	const {ref, isTruncated: isOverflowing, measure} = useIsTruncated<HTMLSpanElement>();
	const [isOpen, setIsOpen] = useState(false);
	const [hasFocusableAncestor, setHasFocusableAncestor] = useState(false);
	const isTruncated = isOverflowing || children !== text;

	useLayoutEffect(() => {
		const element = ref.current;
		if (element === null) {
			return;
		}
		measure();
		const observer = new ResizeObserver(measure);
		observer.observe(element);
		return () => observer.disconnect();
	}, [ref, measure, text, children]);

	useLayoutEffect(() => {
		const ancestor = ref.current?.parentElement?.closest(FOCUSABLE_ANCESTOR_SELECTOR);
		setHasFocusableAncestor(ancestor !== null && ancestor !== undefined);
		if (ancestor === null || ancestor === undefined) {
			return;
		}
		const open = () => setIsOpen(true);
		const close = () => setIsOpen(false);
		ancestor.addEventListener('focus', open);
		ancestor.addEventListener('blur', close);
		return () => {
			ancestor.removeEventListener('focus', open);
			ancestor.removeEventListener('blur', close);
		};
	}, [ref]);

	return (
		<Tooltip open={isTruncated && isOpen} onOpenChange={setIsOpen}>
			<TooltipTrigger asChild>
				<span
					ref={ref}
					tabIndex={isTruncated && !hasFocusableAncestor ? 0 : undefined}
					className={cn('block overflow-hidden text-ellipsis whitespace-nowrap', className)}
				>
					{children}
				</span>
			</TooltipTrigger>
			<TooltipContent side="bottom" className="max-w-md break-words whitespace-pre-wrap">
				{text}
			</TooltipContent>
		</Tooltip>
	);
};

export {TruncatedText};
