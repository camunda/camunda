/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Link, Stack } from "@carbon/react";
import { Launch } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { Description, Title, Grid, Content } from "./components";
import ForbiddenIcon from "src/assets/images/forbidden.svg";
import { docsUrl } from "src/configuration";

const ForbiddenPage: FC = () => {
  const { t, Translate } = useTranslate();

  return (
    <Grid>
      <Content gap={6}>
        <ForbiddenIcon />
        <Stack gap={3}>
          <Title>{t("forbiddenPageTitle")}</Title>
          <Description>
            <Translate
              i18nKey="forbiddenPageDesc"
              components={{
                strong: <strong />,
              }}
            />
          </Description>
        </Stack>
        <Link href={docsUrl} target="_blank" renderIcon={Launch}>
          {t("forbiddenPageLinkLabel")}
        </Link>
      </Content>
    </Grid>
  );
};

export default ForbiddenPage;
