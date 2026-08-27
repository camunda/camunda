/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { UseEntityModalCustomProps } from "src/components/modal";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { roleMutations } from "src/utility/api/roles/mutations";
import { MappingRuleMultiSelect } from "src/components/form/entitySelection/MappingRuleSelection";
import FormModal from "src/components/modal/FormModal";
import type { MappingRule, Role } from "@camunda/camunda-api-zod-schemas/8.10";

const AssignMappingRulesModal: FC<
  UseEntityModalCustomProps<
    { id: Role["roleId"] },
    { assignedMappingRules: MappingRule[] }
  >
> = ({ entity: role, assignedMappingRules, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("roles");
  const [selectedMappingRules, setSelectedMappingRules] = useState<
    MappingRule[]
  >([]);
  const [loadingAssignMappingRule, setLoadingAssignMappingRule] =
    useState(false);

  const qc = useQueryClient();
  const { mutateAsync: callAssignMappingRule } = useMutation(
    roleMutations.assignMappingRule(qc),
  );

  const canSubmit = role && selectedMappingRules.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignMappingRule(true);
    try {
      await Promise.all(
        selectedMappingRules.map(({ mappingRuleId }) =>
          callAssignMappingRule({
            mappingRuleId: mappingRuleId,
            roleId: role.id,
          }),
        ),
      );
      onSuccess();
    } catch {
      // error handled globally
    } finally {
      setLoadingAssignMappingRule(false);
    }
  };

  useEffect(() => {
    if (open) {
      setSelectedMappingRules([]);
    }
  }, [open]);

  return (
    <FormModal
      headline={t("assignMappingRule")}
      confirmLabel={t("assignMappingRule")}
      loading={loadingAssignMappingRule}
      loadingDescription={t("assigningMappingRule")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
      overflowVisible
    >
      <p>
        <Translate i18nKey="searchAndAssignMappingRuleToRole">
          Search and assign mapping rule to role
        </Translate>
      </p>
      <MappingRuleMultiSelect
        value={selectedMappingRules}
        onChange={setSelectedMappingRules}
        excluded={assignedMappingRules}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignMappingRulesModal;
