import { FC } from "react";
import {
  DeleteModal as Modal,
  UseEntityModalCustomProps,
} from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api/hooks";
import { useNotifications } from "src/components/notifications";
import { unassignGroupMember } from "src/utility/api/membership";
import { User } from "src/utility/api/users";

type RemoveGroupMemberModalProps = UseEntityModalCustomProps<
  User,
  {
    group: string;
  }
>;

const DeleteModal: FC<RemoveGroupMemberModalProps> = ({
  entity: user,
  open,
  onClose,
  onSuccess,
  group,
}) => {
  const { t } = useTranslate();
  const { enqueueNotification } = useNotifications();

  const [callUnassignMember, { loading }] = useApiCall(unassignGroupMember);

  const handleSubmit = async () => {
    if (group && user) {
      const { success } = await callUnassignMember({
        groupId: group,
        userId: user.id!,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("Group member has been removed."),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t(
        'Are you sure you want to remove "{{ memberName }}" from the group?',
        {
          memberName: user?.username,
        },
      )}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Removing member")}
      onClose={onClose}
    />
  );
};

export default DeleteModal;
