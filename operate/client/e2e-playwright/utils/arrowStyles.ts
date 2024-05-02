/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const ARROW_HEAD_LENGTH = 20;
const ARROW_BODY_LENGTH = 90;
const ARROW_BODY_WIDTH = 2;
const ARROW_COLOR = 'red';
const ARROW_HEAD_EDGE_LENGTH = 10;

const commonArrowBodyStyles = `
    z-index: 9999;
    background-color: ${ARROW_COLOR};          
    position: absolute;    
`;

const commonArrowHeadStyles = `
    content: '';
    position: absolute;
    width: 0;
    height: 0;
`;

const getHorizontalArrowBodyStyles = (className: string) => `
    .${className} {    
        ${commonArrowBodyStyles}
        width: ${ARROW_BODY_LENGTH}px;
        height: ${ARROW_BODY_WIDTH}px; 
        left: 0;
        top: 50%;
        transform: translateY(-50%);
    }
`;

const getHorizontalArrowHeadStyles = (
  className: string,
  direction: 'left' | 'right',
) => `
      .${className}::before {
          ${commonArrowHeadStyles}
          border-top: ${ARROW_HEAD_EDGE_LENGTH}px solid transparent;
          border-bottom: ${ARROW_HEAD_EDGE_LENGTH}px solid transparent;
          top: 50%; 
          transform: translateY(-50%);
      
          ${
            direction === 'right'
              ? `
                  border-left: ${ARROW_HEAD_LENGTH}px solid ${ARROW_COLOR};
                  left: 100%;
              `
              : ` border-right: ${ARROW_HEAD_LENGTH}px solid ${ARROW_COLOR};
                  right: 100%; 
              `
          }
  
        }
  `;

const getVerticalArrowBodyStyles = (className: string) => `    
    .${className} {  
        ${commonArrowBodyStyles}
        width: ${ARROW_BODY_WIDTH}px; 
        height: ${ARROW_BODY_LENGTH}px;
        left: 50%; 
        top: 0;
        transform: translateX(-50%);     
    }
`;

const getVerticalArrowHeadStyles = (
  className: string,
  direction: 'up' | 'down',
) => `
    .${className}::before {
        ${commonArrowHeadStyles}
        border-left: ${ARROW_HEAD_EDGE_LENGTH}px solid transparent;
        border-right: ${ARROW_HEAD_EDGE_LENGTH}px solid transparent;
        transform: translateX(-50%); 
        left: 50%; 
       
        ${
          direction === 'down'
            ? `
                  border-top: ${ARROW_HEAD_LENGTH}px solid  ${ARROW_COLOR};
                  top: 100%; 
              `
            : ` 
                  border-bottom: ${ARROW_HEAD_LENGTH}px solid ${ARROW_COLOR}; 
                  bottom: 100%; 
              `
        }
  

    }
`;

const getAdditionalContentStyles = (
  className: string,
  direction: 'left' | 'right' | 'up' | 'down',
  additionalContent?: string,
) => `
    ${
      additionalContent === undefined
        ? ''
        : `
            .${className}::after {
                content: '${additionalContent}';
                color: red;
                font-size: 20px;
                position: absolute;
                ${
                  direction === 'right'
                    ? `                 
                        top: 5px;
                    `
                    : direction === 'down'
                      ? `
                        left: 5px;
                    `
                      : direction === 'left'
                        ? `
                        top: 5px;
                        right: 0px;
                    `
                        : direction === 'up'
                          ? `                 
                        bottom: 0;
                        right: 5px;
                    `
                          : ''
                }

            }
         `
    }
`;

const getArrowStyles = ({
  className,
  direction,
  additionalContent,
}: {
  className: string;
  direction: 'left' | 'right' | 'up' | 'down';
  additionalContent?: string;
}) => {
  return `
    ${
      direction === 'left' || direction === 'right'
        ? `
        ${getHorizontalArrowBodyStyles(className)}
        ${getHorizontalArrowHeadStyles(className, direction)}
      `
        : ''
    }
    ${
      direction === 'up' || direction === 'down'
        ? `
        ${getVerticalArrowBodyStyles(className)}
        ${getVerticalArrowHeadStyles(className, direction)}
      `
        : ''
    }

    ${getAdditionalContentStyles(className, direction, additionalContent)}
    `;
};

export {ARROW_HEAD_LENGTH, ARROW_BODY_LENGTH, getArrowStyles};
