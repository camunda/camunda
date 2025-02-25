import useTranslate from "src/utility/localization";
import { useLocation } from "react-router-dom";
import Users from "src/pages/users";
import Groups from "src/pages/groups";
import Roles from "src/pages/roles";
import Tenants from "src/pages/tenants";
import Mappings from "src/pages/mappings";
import Authorizations from "src/pages/authorizations";

export const useGlobalRoutes = () => {
  const { t } = useTranslate();
  const { pathname } = useLocation();
  const routes = [
    {
      path: "/users/*",
      key: "/users",
      label: t("Users"),
      element: <Users />,
    },
    {
      path: "/groups/*",
      key: "/groups",
      label: t("Groups"),
      element: <Groups />,
    },
    {
      path: "/roles/*",
      key: "/roles",
      label: t("Roles"),
      element: <Roles />,
    },
    {
      path: "/tenants/*",
      key: "/tenants",
      label: t("Tenants"),
      element: <Tenants />,
    },
    {
      path: "/mappings/*",
      key: "/mappings",
      label: t("Mappings"),
      element: <Mappings />,
    },
    {
      path: "/authorizations/*",
      key: "/authorizations",
      label: t("Authorizations"),
      element: <Authorizations />,
    },
  ];

  return routes.map((route) => ({
    ...route,
    isCurrentPage: pathname.startsWith(route.key),
  }));
};
