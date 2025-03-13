import i18next from "i18next";
import en from "./en";

const i18n = i18next.createInstance(
  {
    fallbackLng: "en",
    ns: Object.keys(en),
    defaultNS: "components",
    fallbackNS: "components",
    debug: true,
    interpolation: {
      escapeValue: false,
    },
    nsSeparator: false,
    resources: {
      en,
    },
  },
  () => {},
);

export default i18n;
