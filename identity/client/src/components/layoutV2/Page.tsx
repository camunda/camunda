/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, Fragment, ReactNode } from "react";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
  cn,
  PageHeader as DSPageHeader,
  PageLayout,
  Text,
} from "@camunda/design-system";
import { Link } from "react-router-dom";
import { DocumentationLink } from "src/components/documentationV2";
import useTranslate from "src/utility/localization";

type PageHeaderProps = {
  title: string;
  linkText: string;
  docsLinkPath?: string;
  shouldShowDocumentationLink?: boolean;
};

export const PageHeader: FC<PageHeaderProps> = ({
  title,
  linkText,
  docsLinkPath,
  shouldShowDocumentationLink = true,
}) => {
  const { Translate } = useTranslate();

  return (
    <div className="flex flex-col gap-1 mb-8">
      <DSPageHeader title={title} />
      {shouldShowDocumentationLink && (
        <Text as="p" variant="body-md" className="text-muted-foreground">
          <Translate i18nKey="moreInfo" values={{ linkText }}>
            For more information, see documentation on{" "}
            <DocumentationLink path={docsLinkPath} withIcon>
              {linkText}
            </DocumentationLink>
          </Translate>
        </Text>
      )}
    </div>
  );
};

type BreadcrumbsProps = {
  items: {
    // Omit `href` for the current page — it renders as non-interactive text.
    href?: string;
    title: ReactNode;
  }[];
};

export const Breadcrumbs: FC<BreadcrumbsProps> = ({ items }) => (
  <Breadcrumb>
    <BreadcrumbList>
      {items.map(({ href, title }, index) => (
        <Fragment key={href ?? index}>
          {index > 0 && <BreadcrumbSeparator />}
          <BreadcrumbItem>
            {href ? (
              <BreadcrumbLink asChild>
                <Link to={href}>{title}</Link>
              </BreadcrumbLink>
            ) : (
              <BreadcrumbPage>{title}</BreadcrumbPage>
            )}
          </BreadcrumbItem>
        </Fragment>
      ))}
    </BreadcrumbList>
  </Breadcrumb>
);

// TODO: Reevaluate `PageLayout` usage here when `globals` are migrated. It might actually belong in the `AppRoot`...
const Page: FC<{ children?: ReactNode; className?: string }> = ({
  children,
  className,
}) => <PageLayout className={cn("h-full", className)}>{children}</PageLayout>;

export const StackPage: FC<{ children?: ReactNode }> = ({ children }) => (
  <Page>
    <div className="flex flex-col gap-6">{children}</div>
  </Page>
);

export default Page;
