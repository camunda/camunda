import {
  Trans,
  TransProps,
  useTranslation,
  UseTranslationOptions,
} from "react-i18next";
import i18n from "./i18n";
import { FC } from "react";

const useTranslate = (ns?: string) => {
  let translationOptions: [string | undefined, UseTranslationOptions<string>] =
    [ns, { i18n }];
  if (ns && ns.includes(".")) {
    const translationParts = ns.split(".");
    const actualNs = translationParts.shift();
    translationOptions = [
      actualNs,
      { i18n, keyPrefix: translationParts.join(".") },
    ];
  }

  const { t } = useTranslation(...translationOptions);
  const Translate: FC<Omit<TransProps<string>, "ns" | "t">> = ({
    children,
    ...transProps
  }) => {
    return (
      <Trans {...transProps} i18n={i18n} t={t} ns={translationOptions[0]}>
        {children}
      </Trans>
    );
  };

  return { t, Translate };
};

export default useTranslate;
