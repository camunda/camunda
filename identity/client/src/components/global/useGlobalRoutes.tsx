import useTranslate from "src/utility/localization";
import { useLocation } from "react-router-dom";
import Users from "src/pages/users";
import Groups from "src/pages/groups";
import Roles from "src/pages/roles";
import Tenants from "src/pages/tenants";
import Mappings from "src/pages/mappings";
import Authorizations from "src/pages/authorizations";
import { isOIDC } from "src/configuration";

export const useGlobalRoutes = () => {
  const { t } = useTranslate();
  const { pathname } = useLocation();

  const authTypeDependentRoutes = isOIDC
    ? [
        {
          path: "/mappings/*",
          key: "/mappings",
          label: t("mappings"),
          element: <Mappings />,
        },
      ]
    : [
        {
          path: "/users/*",
          key: "/users",
          label: t("users"),
          element: <Users />,
        },
      ];

  const routes = [
    ...authTypeDependentRoutes,
    {
      path: "/groups/*",
      key: "/groups",
      label: t("groups"),
      element: <Groups />,
    },
    {
      path: "/roles/*",
      key: "/roles",
      label: t("roles"),
      element: <Roles />,
    },
    {
      path: "/tenants/*",
      key: "/tenants",
      label: t("tenants"),
      element: <Tenants />,
    },
    {
      path: "/authorizations/*",
      key: "/authorizations",
      label: t("authorizations"),
      element: <Authorizations />,
    },
  ];

  return routes.map((route) => ({
    ...route,
    isCurrentPage: pathname.startsWith(route.key),
  }));
};
