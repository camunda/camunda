/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import {
  SkeletonText,
  StructuredListBody,
  StructuredListRow,
  StructuredListWrapper,
} from "@carbon/react";
import { cssSize } from "src/utility/style";
import { Cell, HeadCell } from "./components";

type SkeletonProps = {
  entries?: number;
};

const Skeleton: FC<SkeletonProps> = ({ entries = 2 }) => (
  <StructuredListWrapper>
    <StructuredListBody>
      {new Array(entries).fill(undefined).map((_, i) => (
        <StructuredListRow key={`list-skeleton-row-${i}`}>
          <HeadCell head noWrap>
            <SkeletonText heading width={cssSize(20)} />
          </HeadCell>
          <Cell>
            <SkeletonText />
          </Cell>
        </StructuredListRow>
      ))}
    </StructuredListBody>
  </StructuredListWrapper>
);

export default Skeleton;
