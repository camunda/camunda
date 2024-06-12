import { FC, useState } from "react";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page from "src/components/layout/Page";
import EntityList, {
  DocumentationDescription,
} from "src/components/entityList";
import { DocumentationLink } from "src/components/documentation";
import { getGroups, Group } from "src/utility/api/groups";
import { useNavigate } from "react-router";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";

const List: FC = () => {
  const { t, Translate } = useTranslate();
  const navigate = useNavigate();
  const [, setSearch] = useState("");

  const { data: groups, loading, reload, success } = useApi(getGroups);

  const showDetails = ({ id }: Group) => navigate(`${id}`);

  return (
    <Page>
      <EntityList
        title={t("Groups")}
        data={groups}
        headers={[{ header: t("name"), key: "name" }]}
        sortProperty="name"
        onEntityClick={showDetails}
        onSearch={setSearch}
        loading={loading}
      />
      {success && (
        <DocumentationDescription>
          <Translate>Learn more about groups in our</Translate>{" "}
          <DocumentationLink path="/concepts/access-control/groups" />.
        </DocumentationDescription>
      )}
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title="The list of groups could not be loaded."
          actionButton={{ label: "Retry", onClick: reload }}
        />
      )}
    </Page>
  );
};

export default List;
