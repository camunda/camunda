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
import { spacing06, spacing07, spacing09 } from "@carbon/elements";
import { cssSize } from "src/utility/style";
import { useNavigate } from "react-router";
import { DocumentationDescription } from "src/components/entityList";
import { DocumentationLink } from "src/components/documentation";
import useTranslate from "src/utility/localization";

// @TODO: remove PageTitle and use PageHeader component instead for all pages
export const PageTitle = styled.h2`
  margin-bottom: ${cssSize(3)};
  margin-left: ${cssSize(2)};
`;

const Page = styled(Content)`
  height: 100%;

  .cds--css-grid {
    padding-inline: 0;
  }
`;

const PageHeaderContainer = styled.div<{ $largeBottomMargin?: boolean }>`
  margin-bottom: ${({ $largeBottomMargin }) =>
    $largeBottomMargin ? spacing09 : spacing07};
`;

type PageHeaderProps = {
  title: string;
  linkText: string;
  linkUrl: string;
  largeBottomMargin?: boolean;
};

export const PageHeader: FC<PageHeaderProps> = ({
  title,
  linkText,
  linkUrl,
  largeBottomMargin = false,
}) => {
  const { Translate } = useTranslate();
  return (
    <PageHeaderContainer $largeBottomMargin={largeBottomMargin}>
      <h1>{title}</h1>
      <DocumentationDescription>
        <Translate>For more information, see documentation on</Translate>{" "}
        <DocumentationLink path={linkUrl} withIcon={true}>
          {linkText}
        </DocumentationLink>
      </DocumentationDescription>
    </PageHeaderContainer>
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
