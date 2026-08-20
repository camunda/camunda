/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ButtonSkeleton, ContainedList, ContainedListItem, Section, SkeletonText, TabsSkeleton} from '@carbon/react';
import styles from './DetailsSkeleton.module.scss';
import layoutStyles from './taskDetailsLayoutCommon.module.scss';

type Props = {
	'data-testid'?: string;
};

const DetailsSkeleton: React.FC<Props> = (props) => {
	return (
		<div className={layoutStyles.container} data-testid={props['data-testid']}>
			<Section className={layoutStyles.content}>
				<header className={layoutStyles.header}>
					<div className={layoutStyles.headerLeftContainer}>
						<SkeletonText width="150px" />
						<SkeletonText width="100px" className={styles.margin0} />
					</div>
					<div className={layoutStyles.headerRightContainer}>
						<SkeletonText width="100px" className={styles.margin0} />
						<ButtonSkeleton size="sm" />
					</div>
				</header>
				<TabsSkeleton className={layoutStyles.tabs} />
			</Section>
			<aside className={layoutStyles.aside}>
				<ContainedList label={<SkeletonText width="100px" className={styles.margin0} />} kind="disclosed">
					<ContainedListItem>
						<SkeletonText width="75px" />
						<SkeletonText width="125px" />
					</ContainedListItem>
					<ContainedListItem>
						<SkeletonText width="75px" />
						<SkeletonText width="125px" />
					</ContainedListItem>
					<ContainedListItem>
						<SkeletonText width="75px" />
						<SkeletonText width="125px" />
					</ContainedListItem>
					<ContainedListItem>
						<SkeletonText width="75px" />
						<SkeletonText width="125px" />
					</ContainedListItem>
					<ContainedListItem>
						<SkeletonText width="75px" />
						<SkeletonText width="125px" />
					</ContainedListItem>
				</ContainedList>
			</aside>
		</div>
	);
};

export {DetailsSkeleton};
