/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback } from "react";
import { Tag } from "@carbon/react";
import styled from "styled-components";
import useTranslate from "src/utility/localization";
import type { EntitySearchDropdown } from "src/components/form/EntitySearchDropdown";

const SelectedEntities = styled.div`
  margin-top: 0;
`;

type EntitySearchMultiSelectProps<Entity> = {
  searchDropdown: EntitySearchDropdown<Entity>;
  getId: (entity: Entity) => string;
  value: Entity[];
  onChange: (entities: Entity[]) => void;
  excluded?: Entity[];
  placeholder: string;
  errorTitle: string;
  autoFocus?: boolean;
};

const EntitySearchMultiSelect = <Entity,>({
  searchDropdown: SearchDropdown,
  getId,
  value,
  onChange,
  excluded = [],
  placeholder,
  errorTitle,
  autoFocus = false,
}: EntitySearchMultiSelectProps<Entity>) => {
  const { t } = useTranslate();

  const isAlreadyPicked = useCallback(
    (entity: Entity) =>
      excluded.some((picked) => getId(picked) === getId(entity)) ||
      value.some((picked) => getId(picked) === getId(entity)),
    [excluded, value, getId],
  );

  const handleSelect = (entity: Entity) => {
    onChange([...value, entity]);
  };

  const handleUnselect = (entity: Entity) => () => {
    onChange(value.filter((picked) => getId(picked) !== getId(entity)));
  };

  return (
    <>
      {value.length > 0 && (
        <SelectedEntities>
          {value.map((entity) => (
            <Tag
              key={getId(entity)}
              onClose={handleUnselect(entity)}
              size="md"
              type="blue"
              filter
            >
              {getId(entity)}
            </Tag>
          ))}
        </SelectedEntities>
      )}
      <SearchDropdown
        autoFocus={autoFocus}
        placeholder={placeholder}
        onSelect={handleSelect}
        filter={(entity) => !isAlreadyPicked(entity)}
        errorTitle={errorTitle}
        retryLabel={t("retry")}
      />
    </>
  );
};

export default EntitySearchMultiSelect;
