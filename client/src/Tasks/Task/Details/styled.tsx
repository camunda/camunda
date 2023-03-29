/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import {TaskDetailsRow} from 'modules/components/TaskDetailsLayout';
import styled, {css} from 'styled-components';
import {SkeletonText as BaseSkeletonText, Section} from '@carbon/react';

const ClaimButtonContainer = styled.span`
  flex-shrink: 0;
  display: flex;
  align-items: center;
`;

const Header = styled(TaskDetailsRow)`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const HeaderLeftContainer = styled.div`
  display: flex;
  flex-direction: column;
`;

const HeaderRightContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--cds-spacing-05);
`;

const Content = styled(Section)`
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
  align-items: center;
  overflow-y: hidden;
`;

const Aside = styled.aside`
  border-left: 1px solid var(--cds-border-subtle);
  display: flex;
  flex-direction: column;
`;

const Container = styled.div`
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: 1fr ${rem(300)};

  ${Content}, ${Aside} {
    padding-top: var(--cds-spacing-05);
  }
`;

type SkeletonTextProps = {
  $disabledMargin?: boolean;
};

const SkeletonText = styled(BaseSkeletonText)<SkeletonTextProps>`
  ${({$disabledMargin = false}) =>
    $disabledMargin
      ? css`
          margin: 0;
        `
      : undefined}
`;

export {
  ClaimButtonContainer,
  Container,
  Header,
  HeaderLeftContainer,
  HeaderRightContainer,
  Content,
  Aside,
  SkeletonText,
};
