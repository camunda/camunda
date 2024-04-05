/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  Aside,
  Container,
  Content,
  Header,
  HeaderLeftContainer,
  HeaderRightContainer,
  SkeletonText,
} from './styled';
import {
  ButtonSkeleton,
  StructuredListSkeleton,
  ContainedList,
  ContainedListItem,
} from '@carbon/react';
import {TaskDetailsRow} from 'modules/components/TaskDetailsLayout';

type Props = {
  'data-testid'?: string;
};

const DetailsSkeleton: React.FC<Props> = (props) => {
  return (
    <Container data-testid={props['data-testid']}>
      <Content>
        <Header as="header">
          <HeaderLeftContainer>
            <SkeletonText width="150px" />
            <SkeletonText width="100px" $disabledMargin />
          </HeaderLeftContainer>
          <HeaderRightContainer>
            <SkeletonText width="100px" $disabledMargin />
            <ButtonSkeleton size="sm" />
          </HeaderRightContainer>
        </Header>
        <TaskDetailsRow>
          <SkeletonText width="150px" heading $disabledMargin />
        </TaskDetailsRow>
        <TaskDetailsRow $disabledSidePadding>
          <StructuredListSkeleton />
        </TaskDetailsRow>
      </Content>
      <Aside>
        <ContainedList
          label={<SkeletonText width="100px" $disabledMargin />}
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
      </Aside>
    </Container>
  );
};

export {DetailsSkeleton};
