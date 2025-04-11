/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode } from "react";
import Flex from "src/components/layout/Flex";
import {
  BreadcrumbSkeleton,
  Section,
  SkeletonIcon,
  SkeletonText,
  TabsSkeleton,
} from "@carbon/react";
import { cssSize } from "src/utility/style";
import { StackPage } from "src/components/layout/Page";
import styled from "styled-components";

const StyledSkeletonText = styled(SkeletonText)`
  margin-top: ${cssSize(1)};
`;

const StyledTabsSkeleton = styled(TabsSkeleton)`
  ul {
    display: flex;
    flex-direction: row;
  }
`;

const TabContent = styled.div`
  padding: ${cssSize(1)};
`;

type DetailPageHeaderFallbackProps = { hasOverflowMenu?: boolean };

export const DetailPageHeaderFallback: FC<DetailPageHeaderFallbackProps> = ({
  hasOverflowMenu = true,
}) => {
  return (
    <Flex>
      <StyledSkeletonText heading width={cssSize(20)} />
      {hasOverflowMenu && <SkeletonIcon />}
    </Flex>
  );
};

type DetailPageFallbackProps = {
  children?: ReactNode;
  hasBreadcrumb?: boolean;
};

const DetailPageFallback: FC<DetailPageFallbackProps> = ({
  children,
  hasBreadcrumb = false,
}) => (
  <StackPage>
    <>
      {hasBreadcrumb && <BreadcrumbSkeleton />}
      <DetailPageHeaderFallback />
      <Section>
        <StyledTabsSkeleton />
        <TabContent>{children}</TabContent>
      </Section>
    </>
  </StackPage>
);

export default DetailPageFallback;
