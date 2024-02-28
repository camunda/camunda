/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

type Props = {
  children?: React.ReactNode;
};

const Splitter: React.FC<Props> = ({children}) => {
  return <div>{children}</div>;
};

export default Splitter;
