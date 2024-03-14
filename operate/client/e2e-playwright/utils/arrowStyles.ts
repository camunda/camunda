/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
