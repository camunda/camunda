import { FC, useEffect, useState } from "react";
import { FormModal, UseModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api/hooks";
import { TextField } from "src/components/form";
import { createGroup } from "src/utility/api/groups";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate();

  const [callAddGroup, { loading, namedErrors }, reset] =
    useApiCall(createGroup);

  const [name, setName] = useState("");

  const handleSubmit = async () => {
    const { success } = await callAddGroup({
      name: name.trim(),
    });

    if (success) {
      onSuccess();
    }
  };

  useEffect(() => {
    if (open) {
      setName("");
      reset();
    }
  }, [open]);

  return (
    <FormModal
      open={open}
      headline={t("Create group")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Adding group")}
      confirmLabel={t("Create group")}
    >
      <TextField
        label={t("Name")}
        value={name}
        placeholder={t("My group")}
        onChange={setName}
        errors={namedErrors?.name}
        autofocus
      />
    </FormModal>
  );
};

export default AddModal;
