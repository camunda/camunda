import { useContext } from "react";
import NotificationContext, {
  NotificationContextValue,
} from "./NotificationContext";

const useNotifications = (): NotificationContextValue =>
  useContext(NotificationContext);

export default useNotifications;
