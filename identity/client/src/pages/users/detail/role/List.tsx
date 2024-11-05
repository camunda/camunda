import { FC } from "react";
import { TrashCan } from "@carbon/react/icons";
import EntityList, {
  DocumentationDescription,
  NoDataBody,
  NoDataContainer,
  NoDataHeader,
} from "src/components/entityList";
import useTranslate from "src/utility/localization";
import { useEntityModal } from "src/components/modal";
import { User } from "src/utility/api/users";
import { getUserRoles } from "src/utility/api/users/roles";
import AddModal from "./AddModal";
import DeleteModal from "./DeleteModal";
import { DocumentationLink } from "src/components/documentation";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useApi } from "src/utility/api";

type RolesListProps = {
  user: User;
  loadingUser: boolean;
};

const List: FC<RolesListProps> = ({ user, loadingUser }) => {
  const { t, Translate } = useTranslate();

  const {
    data: roles,
    loading: loadingRoles,
    success,
    reload,
  } = useApi(getUserRoles, { id: user.id! });

  const loading = loadingUser || loadingRoles;

  const [assignRole, addModal] = useEntityModal(AddModal, reload, {
    userRoles: roles || [],
  });
  const [removeRole, deleteModal] = useEntityModal(DeleteModal, reload);

  const areRolesEmpty = !roles || roles.length === 0;

  const documentationReference = (
    <Translate>
      Learn more about assigning roles to users in our{" "}
      <DocumentationLink path="/identity/user-guide/assigning-a-role-to-a-user" />
      .
    </Translate>
  );

  return (
    <>
      <EntityList
        title={t("Roles assigned to user")}
        data={roles}
        headers={[
          { header: t("Name"), key: "name" },
          { header: t("Description"), key: "description" },
        ]}
        addEntityLabel="Assign roles"
        onAddEntity={() => assignRole(user)}
        menuItems={[
          {
            label: t("Delete"),
            onClick: removeRole,
            isDangerous: true,
            icon: TrashCan,
          },
        ]}
        loading={loadingRoles}
      />
      {success && !areRolesEmpty && (
        <DocumentationDescription>
          {documentationReference}
        </DocumentationDescription>
      )}
      {!loading && areRolesEmpty && (
        <div>
          {success && (
            <NoDataContainer>
              <NoDataHeader>
                <Translate>No roles assigned to this user</Translate>
              </NoDataHeader>
              <NoDataBody>{documentationReference}</NoDataBody>
            </NoDataContainer>
          )}
        </div>
      )}
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title="The list of roles could not be loaded."
          actionButton={{ label: "Retry", onClick: reload }}
        />
      )}
      {addModal}
      {deleteModal}
    </>
  );
};

export default List;
