import { C3Navigation } from "@camunda/camunda-composite-components";
import { useGlobalRoutes } from "src/components/global/useGlobalRoutes";
import { useNavigate } from "react-router";
import { useApi } from "src/utility/api";
import { checkLicense, License } from "src/utility/api/headers";
import { getAuthentication } from "src/utility/api/authentication";
import { ArrowRight } from "@carbon/react/icons";
import { logout } from "src/utility/auth";

const AppHeader = ({ hideNavLinks = false }) => {
  const routes = useGlobalRoutes();
  const navigate = useNavigate();
  const { data: license } = useApi(checkLicense);
  const { data: camundaUser } = useApi(getAuthentication);

  return (
    <C3Navigation
      app={{
        name: "Identity",
        ariaLabel: "Identity",
        routeProps: {
          href: "/",
        },
      }}
      appBar={{
        isOpen: false,
        elements: [],
      }}
      navbar={{
        elements: hideNavLinks
          ? []
          : routes.map((route) => ({
              ...route,
              routeProps: {
                onClick: () => navigate(route.key),
              },
            })),
        licenseTag: getLicenseTag(license),
      }}
      userSideBar={{
        version: import.meta.env.VITE_APP_VERSION,
        ariaLabel: "Settings",
        customElements: {
          profile: {
            label: "Profile",
            user: {
              name: camundaUser?.displayName ?? "",
              email: camundaUser?.email ?? "",
            },
          },
        },
        elements: [
          {
            key: "terms",
            label: "Terms of use",
            onClick: () => {
              window.open(
                "https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/",
                "_blank",
              );
            },
          },
          {
            key: "privacy",
            label: "Privacy policy",
            onClick: () => {
              window.open("https://camunda.com/legal/privacy/", "_blank");
            },
          },
          {
            key: "imprint",
            label: "Imprint",
            onClick: () => {
              window.open("https://camunda.com/legal/imprint/", "_blank");
            },
          },
        ],
        bottomElements: camundaUser?.canLogout
          ? [
              {
                key: "logout",
                label: "Log out",
                renderIcon: ArrowRight,
                kind: "ghost",
                onClick: logout,
              },
            ]
          : undefined,
      }}
    />
  );
};

function getLicenseTag(license: License | null) {
  if (license === null) {
    return {
      show: true,
      isProductionLicense: false,
      isCommercial: false,
    };
  }

  return {
    show: license.licenseType === undefined || license.licenseType != "saas",
    isProductionLicense: license.validLicense,
    isCommercial: license.isCommercial,
    expiresAt: license.expiresAt ?? undefined,
  };
}

export default AppHeader;
