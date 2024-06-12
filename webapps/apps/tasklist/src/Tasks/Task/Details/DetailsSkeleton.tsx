/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ButtonSkeleton,
  StructuredListSkeleton,
  ContainedList,
  ContainedListItem,
  Section,
  SkeletonText,
  TabsSkeleton,
} from '@carbon/react';
import {TaskDetailsRow} from 'modules/components/TaskDetailsLayout';
import styles from './styles.module.scss';
import headerStyles from './Header.module.scss';
import asideStyles from './Aside.module.scss';
import tabStyles from './TabListNav/styles.module.scss';

type Props = {
  'data-testid'?: string;
};

const DetailsSkeleton: React.FC<Props> = (props) => {
  return (
    <div className={styles.container} data-testid={props['data-testid']}>
      <Section className={styles.content}>
        <header className={headerStyles.header}>
          <div className={headerStyles.headerLeftContainer}>
            <SkeletonText width="150px" />
            <SkeletonText width="100px" className={styles.margin0} />
          </div>
          <div className={headerStyles.headerRightContainer}>
            <SkeletonText width="100px" className={styles.margin0} />
            <ButtonSkeleton size="sm" />
          </div>
        </header>
        <TabsSkeleton className={tabStyles.tabs} />
        <TaskDetailsRow>
          <SkeletonText width="150px" heading />
        </TaskDetailsRow>
        <TaskDetailsRow $disabledSidePadding>
          <StructuredListSkeleton />
        </TaskDetailsRow>
      </Section>
      <aside className={asideStyles.aside}>
        <ContainedList
          label={<SkeletonText width="100px" className={styles.margin0} />}
          kind="disclosed"
        >
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
