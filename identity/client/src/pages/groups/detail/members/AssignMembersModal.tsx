import { FC, useEffect, useState } from "react";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import { assignGroupMember } from "src/utility/api/membership";
import useTranslate from "src/utility/localization";
import { useApi, useApiCall } from "src/utility/api/hooks";
import { searchUser, User } from "src/utility/api/users";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import styled from "styled-components";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { Group } from "src/utility/api/groups";

const SelectedUsers = styled.div`
  margin-top: 0;
`;

const AssignMembersModal: FC<
  UseEntityModalCustomProps<
    { id: Group["groupKey"] },
    { assignedUsers: User[] }
  >
> = ({ entity: group, assignedUsers, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate();
  const [selectedUsers, setSelectedUsers] = useState<User[]>([]);
  const [loadingAssignUser, setLoadingAssignUser] = useState(false);

  const {
    data: userSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchUser);
  const [callAssignUser] = useApiCall(assignGroupMember);

  const unassignedUsers =
    userSearchResults?.items.filter(
      ({ id }) =>
        !assignedUsers.some((user) => user.id === id) &&
        !selectedUsers.some((user) => user.id === id),
    ) || [];

  const onSelectUser = (user: User) => {
    setSelectedUsers([...selectedUsers, user]);
  };

  const onUnselectUser =
    ({ id }: User) =>
    () => {
      setSelectedUsers(selectedUsers.filter((user) => user.id !== id));
    };

  const canSubmit = group && selectedUsers.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignUser(true);

    const results = await Promise.all(
      selectedUsers.map(({ id }) =>
        callAssignUser({ userId: id!, groupId: group.id }),
      ),
    );

    setLoadingAssignUser(false);

    if (results.every(({ success }) => success)) {
      onSuccess();
    }
  };

  useEffect(() => {
    if (open) {
      setSelectedUsers([]);
    }
  }, [open]);

  return (
    <FormModal
      headline={t("Assign members")}
      confirmLabel={t("Assign")}
      loading={loadingAssignUser}
      loadingDescription={t("Assigning members")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
      overflowVisible
    >
      <p>
        <Translate>Search and assign members to group.</Translate>
      </p>
      {selectedUsers.length > 0 && (
        <SelectedUsers>
          {selectedUsers.map((user) => (
            <Tag
              key={user.id}
              onClose={onUnselectUser(user)}
              size="md"
              type="blue"
              filter
            >
              {user.username}
            </Tag>
          ))}
        </SelectedUsers>
      )}
      <DropdownSearch
        autoFocus
        items={unassignedUsers}
        itemTitle={({ username }) => username}
        itemSubTitle={({ email }) => email}
        placeholder={t("Search by full name or email address")}
        onSelect={onSelectUser}
      />
      {!loading && error && (
        <TranslatedErrorInlineNotification
          title={t("Users could not be loaded.")}
          actionButton={{ label: t("Retry"), onClick: reload }}
        />
      )}
    </FormModal>
  );
};

export default AssignMembersModal;
