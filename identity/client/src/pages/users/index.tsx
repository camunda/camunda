import { FC, useState } from "react";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page from "src/components/layout/Page";
import EntityList, {
  DocumentationDescription,
} from "src/components/entityList";
import { DocumentationLink } from "src/components/documentation";
import { getUsers, User } from "src/utility/api/users";
import { useNavigate } from "react-router";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";

const List: FC = () => {
  const { t, Translate } = useTranslate();
  const navigate = useNavigate();
  const [, setSearch] = useState("");

  const { data: users, loading, reload, success } = useApi(getUsers);

  const showDetails = ({ id }: User) => navigate(`${id}`);

  return (
    <Page>
      <EntityList
        title={t("Users")}
        data={users}
        headers={[
          { header: t("Username"), key: "username" },
          { header: t("Email"), key: "email" },
        ]}
        sortProperty="username"
        onEntityClick={showDetails}
        onSearch={setSearch}
        loading={loading}
      />
      {success && (
        <DocumentationDescription>
          <Translate>Learn more about users in our</Translate>{" "}
          <DocumentationLink path="/concepts/access-control/users" />.
        </DocumentationDescription>
      )}
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title="The list of users could not be loaded."
          actionButton={{ label: "Retry", onClick: reload }}
        />
      )}
    </Page>
  );
};

export default List;
