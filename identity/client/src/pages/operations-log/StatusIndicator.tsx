/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { CheckmarkFilled, ErrorFilled } from "@carbon/react/icons";

type Props = {
  status: "SUCCESS" | "FAIL";
};

const StatusIndicator: FC<Props> = ({ status }) => {
  const isSuccess = status === "SUCCESS";
  const Icon = isSuccess ? CheckmarkFilled : ErrorFilled;
  const color = isSuccess
    ? "var(--cds-support-success)"
    : "var(--cds-support-error)";
  const label = isSuccess ? "Successful" : "Failed";

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: "var(--cds-spacing-02)",
      }}
    >
      <Icon size={16} style={{ color }} />
      <span>{label}</span>
    </div>
  );
};

export { StatusIndicator };

