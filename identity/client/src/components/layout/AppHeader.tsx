import { C3Navigation } from "@camunda/camunda-composite-components";
import { useGlobalRoutes } from "src/components/global/useGlobalRoutes";
import { useNavigate } from "react-router";
import { useApi } from "src/utility/api";
import { checkLicense } from "src/utility/api/headers";

const AppHeader = () => {
  const routes = useGlobalRoutes();
  const navigate = useNavigate();
  const { data: license } = useApi(checkLicense);
  const showLicense: boolean =
    license == null
      ? true
      : license.licenseType === undefined || license.licenseType != "saas";
  const isProductionLicense: boolean =
    license == null ? false : license.validLicense;

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
        licenseTag: {
          show: showLicense,
          isProductionLicense: isProductionLicense,
        },
      }}
    />
  );
};

export default AppHeader;
