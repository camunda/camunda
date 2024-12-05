import { FC, StrictMode } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { baseUrl } from "./configuration";
import AppRoot from "./components/global/AppRoot";
import GlobalRoutes from "src/components/global/GlobalRoutes";
import { LoginPage } from "src/pages/login/LoginPage.tsx";

const App: FC = () => {
  return (
    <BrowserRouter>
      <StrictMode>
        <Routes>
          <Route key="login" path="/login" Component={LoginPage} />
          <Route
            key="identity-ui"
            path={`${baseUrl}/*`}
            element={
              <AppRoot>
                <GlobalRoutes />
              </AppRoot>
            }
          />
        </Routes>
      </StrictMode>
    </BrowserRouter>
  );
};

export default App;
