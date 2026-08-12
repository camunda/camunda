/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useEffect, useState } from "react";
import { FormLabel, Tag } from "@carbon/react";
import useTranslate from "src/utility/localization";
import type { EntitySearchDropdown } from "src/components/form/EntitySearchDropdown";

type OwnerSelectionSearchProps<Entity> = {
  searchDropdown: EntitySearchDropdown<Entity>;
  getId: (entity: Entity) => string;
  itemToString: (entity: Entity) => string;
  errorTitle: string;
  onChange: (ownerId: string) => void;
  ownerId?: string;
  isEmpty?: boolean;
};

const OwnerSelectionSearch = <Entity,>({
  searchDropdown: SearchDropdown,
  getId,
  itemToString,
  errorTitle,
  onChange,
  ownerId,
  isEmpty = false,
}: OwnerSelectionSearchProps<Entity>) => {
  const { t } = useTranslate("authorizations");
  const [selectedEntity, setSelectedEntity] = useState<Entity | null>(null);

  useEffect(() => {
    if (!ownerId) {
      setSelectedEntity(null);
    }
  }, [ownerId]);

  const handleSelect = (entity: Entity) => {
    setSelectedEntity(entity);
    onChange(getId(entity));
  };

  const handleClear = () => {
    setSelectedEntity(null);
    onChange("");
  };

  return (
    <div>
      <FormLabel>{t("owner")}</FormLabel>
      {selectedEntity ? (
        <div style={{ marginTop: "0.5rem" }}>
          <Tag filter onClose={handleClear} type="blue" size="md">
            {itemToString(selectedEntity)}
          </Tag>
        </div>
      ) : (
        <SearchDropdown
          placeholder={t("searchByOwnerId")}
          onSelect={handleSelect}
          errorTitle={errorTitle}
          retryLabel={t("retry")}
        />
      )}
      {isEmpty && (
        <p
          style={{
            color: "var(--cds-text-error, #da1e28)",
            fontSize: "0.75rem",
            marginTop: "0.25rem",
          }}
        >
          {t("ownerRequired")}
        </p>
      )}
    </div>
  );
};

export default OwnerSelectionSearch;
