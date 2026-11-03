/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {Skeleton} from '@camunda/design-system';

type Props = {
	scrollRef: React.RefObject<HTMLElement | null>;
	revision: string | number;
	onReach: () => void;
};

const HistoryPagingSentinel: React.FC<Props> = ({scrollRef, revision, onReach}) => {
	const {t} = useTranslation();
	const ref = useRef<HTMLSpanElement>(null);
	const onReachRef = useRef(onReach);
	const isVisibleRef = useRef(false);

	useEffect(() => {
		onReachRef.current = onReach;
	});

	useEffect(() => {
		if (isVisibleRef.current) {
			onReachRef.current();
		}
	}, [revision]);

	useEffect(() => {
		const element = ref.current;
		if (!element) {
			return;
		}
		const observer = new IntersectionObserver(
			(entries) => {
				const isVisible = entries[entries.length - 1]?.isIntersecting ?? false;
				if (isVisible && !isVisibleRef.current) {
					onReachRef.current();
				}
				isVisibleRef.current = isVisible;
			},
			{root: scrollRef.current},
		);
		observer.observe(element);
		return () => observer.disconnect();
	}, [scrollRef]);

	return (
		<span ref={ref} className="block w-full">
			<span className="sr-only">{t('operate.processInstance.history.loadingMore')}</span>
			<Skeleton aria-hidden="true" className="h-4 w-full" />
		</span>
	);
};

export {HistoryPagingSentinel};
