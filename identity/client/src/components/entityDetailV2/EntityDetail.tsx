/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode } from "react";
import { Cell, HeadCell } from "./components";
import Skeleton from "./Skeleton";

type EntityDetailProps = {
  label?: string;
  data: {
    label: ReactNode;
    value: ReactNode;
  }[];
  loading?: boolean;
};

const EntityDetail: FC<EntityDetailProps> = ({
  label: listLabel,
  data,
  loading,
}) => {
  const entries = data.length;
  const isDataMissing = data.some(
    ({ value }) => value === undefined || value === null,
  );

  if (loading || isDataMissing) {
    return <Skeleton entries={entries} />;
  }

  return (
    <dl aria-label={listLabel} className="m-0 flex flex-col">
      {data?.map(({ label, value }, idx) => (
        <div
          key={`${label}-${idx}`}
          className="flex gap-4 py-3 shadow-[inset_0_-1px_0_var(--border)]"
        >
          <HeadCell>{label}</HeadCell>
          <Cell>{value}</Cell>
        </div>
      ))}
    </dl>
  );
};

export default EntityDetail;
