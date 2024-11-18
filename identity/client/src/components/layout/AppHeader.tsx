import { C3Navigation } from "@camunda/camunda-composite-components";
import { useGlobalRoutes } from "src/components/global/useGlobalRoutes";
import { useNavigate } from "react-router";
import { useApi } from "src/utility/api";
import { checkLicense, License } from "src/utility/api/headers";

const AppHeader = () => {
  const routes = useGlobalRoutes();
  const navigate = useNavigate();
  const { data: license } = useApi(checkLicense);

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
        elements: routes.map((route) => ({
          ...route,
          routeProps: {
            onClick: () => navigate(route.key),
          },
        })),
        licenseTag: getLicenseTag(license),
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
