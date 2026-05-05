/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { InlineNotification, Stack } from "@carbon/react";
import useTranslate from "src/utility/localization";
import { isLoggedIn } from "src/utility/auth";
import CamundaLogo from "src/assets/images/camunda.svg";
import { Paths } from "src/components/global/routePaths";
import {
  Button,
  Content,
  Header,
  LoginFormContainer,
  LoginPageContainer,
} from "src/pages/login/styled.ts";

interface IdpOption {
  id: string;
  loginUrl: string;
}

interface PickerResponse {
  tenantId: string;
  idps: IdpOption[];
}

export const IdpPickerPage: React.FC = () => {
  const { t } = useTranslate();
  const { tenantId } = useParams<{ tenantId: string }>();
  const navigate = useNavigate();
  const [picker, setPicker] = useState<PickerResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [alreadySignedIn, setAlreadySignedIn] = useState(false);

  useEffect(() => {
    if (isLoggedIn()) {
      navigate(Paths.users());
    }
  }, [navigate]);

  useEffect(() => {
    if (!tenantId) {
      return;
    }
    void fetch(`/login/${encodeURIComponent(tenantId)}`, {
      headers: { Accept: "application/json" },
    })
      .then((response) => {
        if (response.status === 409) {
          setAlreadySignedIn(true);
          return null;
        }
        if (response.status === 404) {
          throw new Error(t("Tenant not found"));
        }
        if (!response.ok) {
          throw new Error(response.statusText);
        }
        return response.json() as Promise<PickerResponse>;
      })
      .then((data) => {
        if (data) {
          setPicker(data);
        }
      })
      .catch((e: Error) => setError(e.message));
  }, [tenantId, t]);

  return (
    <LoginPageContainer>
      <Content>
        <Header>
          <CamundaLogo />
          <h1>{t("Sign in")}</h1>
        </Header>
        <LoginFormContainer $hasError={!!error}>
          {error && (
            <InlineNotification
              title={error}
              hideCloseButton
              kind="error"
              role="alert"
              lowContrast
            />
          )}
          {alreadySignedIn && (
            <Stack gap={4}>
              <p>
                {t(
                  "You're already signed in. Log out first to switch to a different tenant.",
                )}
              </p>
              <Button as="a" href="/logout" kind="primary">
                {t("Log out and continue")}
              </Button>
            </Stack>
          )}
          {!alreadySignedIn && !picker && !error && <p>{t("Loading…")}</p>}
          {!alreadySignedIn && picker && picker.idps.length === 0 && (
            <p>{t("No identity providers configured for this tenant.")}</p>
          )}
          {!alreadySignedIn && (
            <Stack gap={4}>
              {picker?.idps.map((idp) => (
                <Button key={idp.id} as="a" href={idp.loginUrl} kind="primary">
                  {idp.id}
                </Button>
              ))}
            </Stack>
          )}
        </LoginFormContainer>
      </Content>
    </LoginPageContainer>
  );
};
