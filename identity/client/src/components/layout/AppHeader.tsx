import { C3Navigation } from "@camunda/camunda-composite-components";
import { useGlobalRoutes } from "src/components/global/useGlobalRoutes";
import { useNavigate } from "react-router";

const AppHeader = () => {
  const routes = useGlobalRoutes();
  const navigate = useNavigate();

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
      }}
    />
  );
};

export default AppHeader;
