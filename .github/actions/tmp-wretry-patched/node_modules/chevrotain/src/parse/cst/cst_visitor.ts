import isEmpty from "lodash/isEmpty"
import compact from "lodash/compact"
import isArray from "lodash/isArray"
import map from "lodash/map"
import forEach from "lodash/forEach"
import filter from "lodash/filter"
import keys from "lodash/keys"
import isFunction from "lodash/isFunction"
import isUndefined from "lodash/isUndefined"
import includes from "lodash/includes"
import { defineNameProp } from "../../lang/lang_extensions"
import { CstNode, ICstVisitor } from "@chevrotain/types"

export function defaultVisit<IN>(ctx: any, param: IN): void {
  const childrenNames = keys(ctx)
  const childrenNamesLength = childrenNames.length
  for (let i = 0; i < childrenNamesLength; i++) {
    const currChildName = childrenNames[i]
    const currChildArray = ctx[currChildName]
    const currChildArrayLength = currChildArray.length
    for (let j = 0; j < currChildArrayLength; j++) {
      const currChild: any = currChildArray[j]
      // distinction between Tokens Children and CstNode children
      if (currChild.tokenTypeIdx === undefined) {
        this[currChild.name](currChild.children, param)
      }
    }
  }
  // defaultVisit does not support generic out param
}

export function createBaseSemanticVisitorConstructor(
  grammarName: string,
  ruleNames: string[]
): {
  new (...args: any[]): ICstVisitor<any, any>
} {
  const derivedConstructor: any = function () {}

  // can be overwritten according to:
  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/
  // name?redirectlocale=en-US&redirectslug=JavaScript%2FReference%2FGlobal_Objects%2FFunction%2Fname
  defineNameProp(derivedConstructor, grammarName + "BaseSemantics")

  const semanticProto = {
    visit: function (cstNode: CstNode | CstNode[], param: any) {
      // enables writing more concise visitor methods when CstNode has only a single child
      if (isArray(cstNode)) {
        // A CST Node's children dictionary can never have empty arrays as values
        // If a key is defined there will be at least one element in the corresponding value array.
        cstNode = cstNode[0]
      }

      // enables passing optional CstNodes concisely.
      if (isUndefined(cstNode)) {
        return undefined
      }

      return this[cstNode.name](cstNode.children, param)
    },

    validateVisitor: function () {
      const semanticDefinitionErrors = validateVisitor(this, ruleNames)
      if (!isEmpty(semanticDefinitionErrors)) {
        const errorMessages = map(
          semanticDefinitionErrors,
          (currDefError) => currDefError.msg
        )
        throw Error(
          `Errors Detected in CST Visitor <${this.constructor.name}>:\n\t` +
            `${errorMessages.join("\n\n").replace(/\n/g, "\n\t")}`
        )
      }
    }
  }

  derivedConstructor.prototype = semanticProto
  derivedConstructor.prototype.constructor = derivedConstructor

  derivedConstructor._RULE_NAMES = ruleNames

  return derivedConstructor
}

export function createBaseVisitorConstructorWithDefaults(
  grammarName: string,
  ruleNames: string[],
  baseConstructor: Function
): {
  new (...args: any[]): ICstVisitor<any, any>
} {
  const derivedConstructor: any = function () {}

  // can be overwritten according to:
  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/
  // name?redirectlocale=en-US&redirectslug=JavaScript%2FReference%2FGlobal_Objects%2FFunction%2Fname
  defineNameProp(derivedConstructor, grammarName + "BaseSemanticsWithDefaults")

  const withDefaultsProto = Object.create(baseConstructor.prototype)
  forEach(ruleNames, (ruleName) => {
    withDefaultsProto[ruleName] = defaultVisit
  })

  derivedConstructor.prototype = withDefaultsProto
  derivedConstructor.prototype.constructor = derivedConstructor

  return derivedConstructor
}

export enum CstVisitorDefinitionError {
  REDUNDANT_METHOD,
  MISSING_METHOD
}

export interface IVisitorDefinitionError {
  msg: string
  type: CstVisitorDefinitionError
  methodName: string
}

export function validateVisitor(
  visitorInstance: ICstVisitor<unknown, unknown>,
  ruleNames: string[]
): IVisitorDefinitionError[] {
  const missingErrors = validateMissingCstMethods(visitorInstance, ruleNames)
  const redundantErrors = validateRedundantMethods(visitorInstance, ruleNames)

  return missingErrors.concat(redundantErrors)
}

export function validateMissingCstMethods(
  visitorInstance: ICstVisitor<unknown, unknown>,
  ruleNames: string[]
): IVisitorDefinitionError[] {
  const missingRuleNames = filter(ruleNames, (currRuleName) => {
    return isFunction((visitorInstance as any)[currRuleName]) === false
  })

  const errors: IVisitorDefinitionError[] = map(
    missingRuleNames,
    (currRuleName) => {
      return {
        msg: `Missing visitor method: <${currRuleName}> on ${<any>(
          visitorInstance.constructor.name
        )} CST Visitor.`,
        type: CstVisitorDefinitionError.MISSING_METHOD,
        methodName: currRuleName
      }
    }
  )

  return compact<IVisitorDefinitionError>(errors)
}

const VALID_PROP_NAMES = ["constructor", "visit", "validateVisitor"]
export function validateRedundantMethods(
  visitorInstance: ICstVisitor<unknown, unknown>,
  ruleNames: string[]
): IVisitorDefinitionError[] {
  const errors = []

  for (const prop in visitorInstance) {
    if (
      isFunction((visitorInstance as any)[prop]) &&
      !includes(VALID_PROP_NAMES, prop) &&
      !includes(ruleNames, prop)
    ) {
      errors.push({
        msg:
          `Redundant visitor method: <${prop}> on ${<any>(
            visitorInstance.constructor.name
          )} CST Visitor\n` +
          `There is no Grammar Rule corresponding to this method's name.\n`,
        type: CstVisitorDefinitionError.REDUNDANT_METHOD,
        methodName: prop
      })
    }
  }
  return errors
}
