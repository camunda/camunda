import { FC } from "react";
import { TrashCan } from "@carbon/react/icons";
import EntityList, {
  DocumentationDescription,
  NoDataBody,
  NoDataContainer,
  NoDataHeader,
} from "src/components/entityList";
import useTranslate from "src/utility/localization";
import { User } from "src/utility/api/users";
import { getUserAuthorizations } from "src/utility/api/users/authorizations";
import { DocumentationLink } from "src/components/documentation";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useApi } from "src/utility/api";
import { Authorization } from "src/utility/api/authorizations";

type AuthorizationsListProps = {
  user: User;
  loadingUser: boolean;
};

const List: FC<AuthorizationsListProps> = ({ user, loadingUser }) => {
  const { t, Translate } = useTranslate();

  const {
    data: authorizations,
    loading: loadingAuthorizations,
    success,
    reload,
  } = useApi(getUserAuthorizations, { key: user.key });

  const loading = loadingUser || loadingAuthorizations;

  const areAuthorizationsEmpty =
    !authorizations || authorizations.items.length === 0;

  const showAuthorizationDetails = (authorization: Authorization) =>
    console.log(authorization);

  const documentationReference = (
    <Translate>
      Learn more about assigning authorizations to users in our{" "}
      <DocumentationLink path="/identity/user-guide/assigning-an-authorization-to-a-user" />
      .
    </Translate>
  );

  return (
    <>
      <EntityList
        title={t("Authorizations assigned to user")}
        data={authorizations == null ? [] : authorizations.items}
        headers={[{ header: t("ResourceType"), key: "resourceType" }]}
        onEntityClick={showAuthorizationDetails}
        addEntityLabel="Assign authorization"
        onAddEntity={() => {}}
        menuItems={[
          {
            label: t("Delete"),
            onClick: () => {},
            isDangerous: true,
            icon: TrashCan,
          },
        ]}
        loading={loadingAuthorizations}
      />
      {success && !areAuthorizationsEmpty && (
        <DocumentationDescription>
          {documentationReference}
        </DocumentationDescription>
      )}
      {!loading && areAuthorizationsEmpty && (
        <div>
          {success && (
            <NoDataContainer>
              <NoDataHeader>
                <Translate>No authorizations assigned to this user</Translate>
              </NoDataHeader>
              <NoDataBody>{documentationReference}</NoDataBody>
            </NoDataContainer>
          )}
        </div>
      )}
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title="The list of authorizations could not be loaded."
          actionButton={{ label: "Retry", onClick: reload }}
        />
      )}
    </>
  );
};

export default List;
