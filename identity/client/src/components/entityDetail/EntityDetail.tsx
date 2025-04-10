/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode } from "react";
import {
  Loading,
  StructuredListBody,
  StructuredListRow,
  StructuredListWrapper,
} from "@carbon/react";
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
  const isDataMissing = data.some(({ value }) => !value);

  return loading && isDataMissing ? (
    <Skeleton entries={entries} />
  ) : (
    <StructuredListWrapper ariaLabel={listLabel}>
      {loading && <Loading />}
      <StructuredListBody>
        {data?.map(({ label, value }, idx) => (
          <StructuredListRow key={`${label}-${idx}`}>
            <HeadCell head noWrap>
              {label}
            </HeadCell>
            <Cell>{value}</Cell>
          </StructuredListRow>
        ))}
      </StructuredListBody>
    </StructuredListWrapper>
  );
};

export default EntityDetail;
