/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// src/pages/Error403.tsx
import { FC } from "react";
import useTranslate from "src/utility/localization";
import {C3EmptyState} from "@camunda/camunda-composite-components";



const Forbidden: FC = () => {
  const { t} = useTranslate();

  return (
      <C3EmptyState
          icon={{
            path: "modules/components/Icon/permission-denied.svg",
            altText: "Permission Denied Icon", // Add a meaningful description for accessibility
          }}
          heading={t("403 - You do not have access to this component")}
          description="It looks like you don't have the necessary permissions to access this component. Please contact your cluster admin to get access."
          link={{
            label: 'Learn more about permissions',
            href: '',
          }}
      />
  );
};

export default Forbidden;
