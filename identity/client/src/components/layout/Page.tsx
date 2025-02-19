/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, PointerEvent, ReactNode } from "react";
import styled from "styled-components";
import { Breadcrumb, BreadcrumbItem, Content, Stack } from "@carbon/react";
import {
  spacing04,
  spacing06,
  spacing07,
  styles,
  textSecondary,
} from "@carbon/elements";
import { cssSize } from "src/utility/style";
import { useNavigate } from "react-router";
import { DocumentationLink } from "src/components/documentation";
import useTranslate from "src/utility/localization";

const PageTitle = styled.h2`
  font-size: ${styles.heading04.fontSize};
  font-weight: ${styles.heading04.fontWeight};
`;

const PageSubTitle = styled.div`
  font-size: ${styles.bodyCompact01.fontSize};
  font-weight: ${styles.bodyCompact01.fontWeight};
  letter-spacing: ${styles.bodyCompact01.letterSpacing};
  letter-height: ${styles.bodyCompact01.lineHeight};
  color: ${textSecondary};
`;

const StackWithMargin = styled(Stack)`
  margin-bottom: ${spacing07};
`;

const Page = styled(Content)`
  height: 100%;
`;

type PageHeaderProps = {
  title: string;
  linkText: string;
  linkUrl: string;
};

export const PageHeader: FC<PageHeaderProps> = ({
  title,
  linkText,
  linkUrl,
}) => {
  const { Translate } = useTranslate();
  return (
    <StackWithMargin gap={spacing04}>
      <PageTitle>{title}</PageTitle>
      <PageSubTitle>
        <Translate>For more information, see documentation on</Translate>{" "}
        <DocumentationLink path={linkUrl} withIcon>
          {linkText}
        </DocumentationLink>
      </PageSubTitle>
    </StackWithMargin>
  );
};

const StyledBreadcrumb = styled(Breadcrumb)`
  margin-left: ${cssSize(2)};
`;

type BreadcrumbsProps = {
  items: {
    href: string;
    title: ReactNode;
  }[];
};

export const Breadcrumbs: FC<BreadcrumbsProps> = ({ items }) => {
  const navigate = useNavigate();
  const onClick = (href: string) => (e: PointerEvent) => {
    navigate(href);
    e.preventDefault();
    e.stopPropagation();
  };

  return (
    <StyledBreadcrumb>
      {items.map(({ href, title }) => (
        <BreadcrumbItem href={href} onClick={onClick(href)} key={href}>
          {title}
        </BreadcrumbItem>
      ))}
    </StyledBreadcrumb>
  );
};

export const StackPage: FC<{ children?: ReactNode }> = ({ children }) => (
  <Page>
    <Stack gap={spacing06}>{children}</Stack>
  </Page>
);

export default Page;
