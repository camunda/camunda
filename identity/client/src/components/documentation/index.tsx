import styled from "styled-components";
import { linkInverse, spacing04 } from "@carbon/elements";
import { Link as BaseLink } from "@carbon/react";
import { FC, ReactNode } from "react";
import useTranslate from "../../utility/localization";
import { docsUrl } from "src/configuration";
import { Launch } from "@carbon/react/icons";

export const DocumentationDescription = styled.p`
  margin-top: ${spacing04};
  max-width: none;
  text-align: left;
`;

const Link = styled(BaseLink)`
  .cds--link__icon {
    margin-inline-start: 0.25rem;
  }
`;

export const LightLink = styled(Link)`
  display: inline;
  color: ${linkInverse} !important;
`;

type DocumentationLinkProps = {
  children?: ReactNode;
  light?: boolean;
  path?: string;
  withIcon?: boolean;
};

export const documentationHref = (
  docsUrl: string,
  path: DocumentationLinkProps["path"],
) => `${docsUrl}${path}`;

export const DocumentationLink: FC<DocumentationLinkProps> = ({
  path = "",
  light = false,
  withIcon = false,
  children,
}) => {
  const LinkComponent = light ? LightLink : Link;
  const { Translate } = useTranslate();

  return (
    <LinkComponent
      href={documentationHref(docsUrl, path)}
      data-test="documentation-link"
      target="_blank"
      renderIcon={withIcon ? () => <Launch aria-label="Launch" /> : undefined}
    >
      {children || <Translate>documentation</Translate>}
    </LinkComponent>
  );
};
