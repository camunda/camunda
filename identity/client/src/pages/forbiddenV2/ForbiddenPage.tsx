/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Heading, Text } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import ForbiddenIcon from "src/assets/images/forbidden.svg";
import { DocumentationLink } from "src/components/documentationV2";

const ForbiddenPage: FC = () => {
  const { t, Translate } = useTranslate();

  return (
    <div className="flex h-full flex-col px-10 pt-8 pb-12">
      <div className="grid flex-1 content-center p-20">
        <div className="flex max-w-100 flex-col gap-6">
          <ForbiddenIcon />
          <div className="flex flex-col gap-2">
            <Heading as="h3" variant="heading-md">
              {t("forbiddenPageTitle")}
            </Heading>
            <Text as="p" variant="body-md">
              <Translate
                i18nKey="forbiddenPageDesc"
                components={{
                  strong: <strong />,
                }}
              />
            </Text>
          </div>
          <DocumentationLink withIcon>
            {t("forbiddenPageLinkLabel")}
          </DocumentationLink>
        </div>
      </div>
    </div>
  );
};

export default ForbiddenPage;
