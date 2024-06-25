import { FC } from "react";
import EntityDetail from "src/components/entityDetail";
import useTranslate from "src/utility/localization";
import { Role } from "src/utility/api/roles";

type RoleDetailsProps = {
  role: Role | null;
  loading: boolean;
};

const RoleDetails: FC<RoleDetailsProps> = ({ role, loading }) => {
  const { t } = useTranslate();

  const { name, description } = role || {};

  return (
    <EntityDetail
      label={t("Role details")}
      data={[
        {
          label: t("Name"),
          value: name,
        },
        {
          label: t("Description"),
          value: description,
        },
      ]}
      loading={loading}
    />
  );
};

export default RoleDetails;
