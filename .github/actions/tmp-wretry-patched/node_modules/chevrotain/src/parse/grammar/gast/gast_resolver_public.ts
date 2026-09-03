import { Rule } from "@chevrotain/gast"
import forEach from "lodash/forEach"
import defaults from "lodash/defaults"
import { resolveGrammar as orgResolveGrammar } from "../resolver"
import { validateGrammar as orgValidateGrammar } from "../checks"
import {
  defaultGrammarResolverErrorProvider,
  defaultGrammarValidatorErrorProvider
} from "../../errors_public"
import { TokenType } from "@chevrotain/types"
import {
  IGrammarResolverErrorMessageProvider,
  IGrammarValidatorErrorMessageProvider,
  IParserDefinitionError
} from "../types"

type ResolveGrammarOpts = {
  rules: Rule[]
  errMsgProvider?: IGrammarResolverErrorMessageProvider
}
export function resolveGrammar(
  options: ResolveGrammarOpts
): IParserDefinitionError[] {
  const actualOptions: Required<ResolveGrammarOpts> = defaults(options, {
    errMsgProvider: defaultGrammarResolverErrorProvider
  })

  const topRulesTable: { [ruleName: string]: Rule } = {}
  forEach(options.rules, (rule) => {
    topRulesTable[rule.name] = rule
  })
  return orgResolveGrammar(topRulesTable, actualOptions.errMsgProvider)
}

export function validateGrammar(options: {
  rules: Rule[]
  maxLookahead: number
  tokenTypes: TokenType[]
  grammarName: string
  errMsgProvider: IGrammarValidatorErrorMessageProvider
}): IParserDefinitionError[] {
  options = defaults(options, {
    errMsgProvider: defaultGrammarValidatorErrorProvider
  })

  return orgValidateGrammar(
    options.rules,
    options.maxLookahead,
    options.tokenTypes,
    options.errMsgProvider,
    options.grammarName
  )
}
